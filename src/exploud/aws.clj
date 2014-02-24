(ns exploud.aws
  (:require [amazonica.core :refer [with-credential]]
            [amazonica.aws
             [autoscaling :as auto]
             [ec2 :as ec2]
             [securitytoken :as sts]
             [sqs :as sqs]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [dire.core :refer [with-pre-hook!]]
            [environ.core :refer :all]
            [exploud
             [asgard :as asgard]
             [util :as util]]))

(def autoscale-queue-name
  "The queue name we'll use for sending announcements."
  "autoscale-announcements")

(def poke-account-id
  "The `poke` account ID."
  (env :aws-poke-account-id))

(def prod-account-id
  "The `prod` account ID."
  (env :aws-prod-account-id))

(def account-ids
  "Our map of account IDs by environment."
  {:poke poke-account-id
   :prod prod-account-id})

(def poke-autoscaling-topic-arn
  "The `poke` autoscaling topic ARN"
  (env :aws-poke-autoscaling-topic-arn))

(def prod-autoscaling-topic-arn
  "The `prod` autoscaling topic ARN"
  (env :aws-prod-autoscaling-topic-arn))

(def autoscaling-topics
  "Our map of autoscaling topics by environment."
  {:poke poke-autoscaling-topic-arn
   :prod prod-autoscaling-topic-arn})

(def role-arn
  "The ARN of the role we want to assume."
  (env :aws-prod-role-arn))

(defn use-current-role?
  "Whether we should use the current IAM role or should assume a role in another account."
  [environment]
  (not= :prod (keyword environment)))

(defn alternative-credentials-if-necessary
  "Attempts to assume a role, if necessary, returning the credentials or nil if current role is to be used."
  [environment]
  (if-not (use-current-role? environment)
    (:credentials (sts/assume-role {:role-arn role-arn :role-session-name "exploud"}))))

(defn account-id
  "Get the account ID we should use for an environment. We'll default to whatever `:poke` uses in the event of not knowing."
  [environment]
  ((keyword environment) account-ids (:poke account-ids)))

(defn autoscaling-topic
  "Get the autoscaling topic ARN we should use for an environment. We'll default ot wahtever `:poke` uses in the event of not knowing."
  [environment]
  ((keyword environment) autoscaling-topics (:poke autoscaling-topics)))

(defn announcement-queue-url
  "Create the URL for an announcement queue in a region and for an environment."
  [region environment]
  (format "https://%s.queue.amazonaws.com/%s/%s" region (account-id environment) autoscale-queue-name))

(defn asg-created-message
  "Create the message describing the creation of an ASG."
  [asg-name]
  {:Message (json/generate-string {:Event "autoscaling:ASG_LAUNCH" :AutoScalingGroupName asg-name})})

(defn asg-deleted-message
  "Create the message describing the deletion of an ASG."
  [asg-name]
  {:Message (json/generate-string {:Event "autoscaling:ASG_TERMINATE" :AutoScalingGroupName asg-name})})

(defn create-tags-on-asg-and-instances
  "Creates tags on an ASG and sets them to propagate at launch. Also creates tags on all instances in that ASG."
  [config environment region asg-name tags]
  (when-let [tag-list (seq (filter :value (map (fn [[k v]] {:resource-id asg-name
                                                           :resource-type "auto-scaling-group"
                                                           :propagate-at-launch true
                                                           :key (name k)
                                                           :value v}) tags)))]
    (auto/create-or-update-tags config :tags (vec tag-list))
    (when-let [instance-ids (seq (map (fn [i] (get-in i [:instance :instanceId])) (asgard/instances-in-asg environment region asg-name)))]
      (let [instance-tag-list (map (fn [t] (select-keys t [:key :value])) tag-list)]
        (ec2/create-tags config :resources (vec instance-ids) :tags (vec instance-tag-list))))))

(defn asg-created
  "Perform AWS-related events that should occur when an ASG has been created."
  [region environment asg-name tags]
  (let [config (merge (alternative-credentials-if-necessary environment)
                      {:endpoint region})]
    (create-tags-on-asg-and-instances config environment region asg-name tags)
    (sqs/send-message config
                      :queue-url (announcement-queue-url region environment)
                      :delay-seconds 0
                      :message-body (json/generate-string (asg-created-message asg-name)))
    (auto/put-notification-configuration config
                                         :auto-scaling-group-name asg-name
                                         :notification-types ["autoscaling:EC2_INSTANCE_LAUNCH" "autoscaling:EC2_INSTANCE_TERMINATE"]
                                         :topic-arn (autoscaling-topic environment))
    nil))

;; Pre-hook attached to `asg-created` to log parameters.
(with-pre-hook! #'asg-created
  (fn [region environment asg-name tags]
    (log/debug "Notifying that" asg-name "has been created in" region environment "with tags" tags)))

(defn asg-deleted
  "Perform AWS-related events that should occur when an ASG has been deleted."
  [region environment asg-name]
  (let [config (merge (alternative-credentials-if-necessary environment) {:endpoint region})]
    (sqs/send-message config
                      :queue-url (announcement-queue-url region environment)
                      :delay-seconds 0
                      :message-body (json/generate-string (asg-deleted-message asg-name)))
    nil))

;; Pre-hook attached to `asg-deleted` to log parameters.
(with-pre-hook! #'asg-deleted
  (fn [region environment asg-name]
    (log/debug "Notifying that" asg-name "has been deleted in" region environment)))

(defn transform-instance-description
  "Takes an aws instance description and returns the fields we are interested in flattened"
  [{:keys [tags instance-id image-id private-ip-address]}]
  {:name (or (some (fn [{k :key v :value}] (when (= k "Name") v)) tags) "none")
   :instance-id instance-id
   :image-id image-id
   :private-ip private-ip-address})

(defn describe-instances
  "Returns a json object describing the instances in the supplied environment
   with the given name and optional state (defaults to running)"
  [environment region name state]
  (let [name (or (and name (.endsWith name "*") name) (str name "*"))
        state (or state "running")
        config (merge (alternative-credentials-if-necessary environment) {:endpoint region})]
    (->> (ec2/describe-instances config
                                 :filters [{:name "tag:Name" :values [name]}
                                           {:name "instance-state-name" :values [state]}])
         :reservations
         (mapcat :instances)
         (map transform-instance-description))))

(defn describe-instances-plain
  "Returns a column formatted string describing the instances in the supplied environment
   with the given name and optional state (defaults to running)"
  [environment region name state]
  (util/as-table (describe-instances environment region name state)))
