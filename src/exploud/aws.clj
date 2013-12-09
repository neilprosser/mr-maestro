(ns exploud.aws
  (:require [amazonica.core :refer [with-credential]]
            [amazonica.aws
             [securitytoken :as sts]
             [sqs :as sqs]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [dire.core :refer [with-pre-hook!]]
            [environ.core :refer :all]))

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
  "Our make of account IDs by environment."
  {:poke poke-account-id
   :prod prod-account-id})

(def role-arn
  "The ARN of the role we want to assume."
  (env :aws-prod-role-arn))

(defn assume-role
  "Attempts to assume a role in the production account, returning the credentials."
  []
  (:credentials (sts/assume-role {:role-arn role-arn
                                  :role-session-name "exploud"})))

(defn use-current-role?
  "Whether we should use the current IAM role or should assume a role in another account."
  [environment]
  (not= :prod (keyword environment)))

(defn account-id
  "Get the account ID we should use for an environment. We'll default to whatever `:poke` uses in the event of not knowing."
  [environment]
  ((keyword environment) account-ids (:poke account-ids)))

(defn announcement-queue-url
  "Create the URL for an announcement queue in a region and for an environment."
  [region environment]
  (format "https://%s.queue.amazonaws.com/%s/%s" region (account-id environment) autoscale-queue-name))

(defn asg-created-message
  "Create the message describing the creation of an ASG."
  [asg-name]
  {:Message (json/generate-string {:Event "autoscaling:ASG_LAUNCH"
                                   :AutoScalingGroupName asg-name})})

(defn asg-deleted-message
  "Create the message describing the deletion of an ASG."
  [asg-name]
  {:Message (json/generate-string {:Event "autoscaling:ASG_TERMINATE"
                                   :AutoScalingGroupName asg-name})})

(defn asg-created
  "Send a message stating that an ASG has been created."
  [region environment asg-name]
  (if (use-current-role? environment)
    (sqs/send-message :queue-url (announcement-queue-url region environment)
                      :delay-seconds 0
                      :message-body (json/generate-string (asg-created-message asg-name)))
    (let [credentials (assume-role)]
      (sqs/send-message credentials
                        :queue-url (announcement-queue-url region environment)
                        :delay-seconds 0
                        :message-body (json/generate-string (asg-created-message asg-name)))))
  nil)

;; Pre-hook attached to `asg-created` to log parameters.
(with-pre-hook! #'asg-created
  (fn [region environment asg-name]
    (log/debug "Notifying that" asg-name "has been created in" region environment)))

(defn asg-deleted
  "Send a message stating that an ASG has been deleted."
  [region environment asg-name]
  (if (use-current-role? environment)
    (sqs/send-message :queue-url (announcement-queue-url region environment)
                      :delay-seconds 0
                      :message-body (json/generate-string (asg-deleted-message asg-name)))
    (let [credentials (assume-role)]
      (sqs/send-message credentials
                        :queue-url (announcement-queue-url region environment)
                        :delay-seconds 0
                        :message-body (json/generate-string (asg-deleted-message asg-name)))))
  nil)

;; Pre-hook attached to `asg-deleted` to log parameters.
(with-pre-hook! #'asg-deleted
  (fn [region environment asg-name]
    (log/debug "Notifying that" asg-name "has been deleted in" region environment)))
