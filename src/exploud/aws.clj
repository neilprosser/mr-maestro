(ns exploud.aws
  (:require [amazonica.core :refer [with-credential]]
            [amazonica.aws
             [autoscaling :as auto]
             [ec2 :as ec2]
             [elasticloadbalancing :as elb]
             [securitytoken :as sts]
             [sqs :as sqs]]
            [cheshire.core :as json]
            [clojure.core.memoize :as memo]
            [clojure.string :as str]
            [io.clj.logging :refer [with-logging-context]]
            [clojure.tools.logging :as log]
            [dire.core :refer [with-pre-hook!]]
            [environ.core :refer :all]
            [exploud
             [numel :as numel]
             [onix :as onix]
             [util :as util]]))

(def ^:private autoscale-queue-name
  "The queue name we'll use for sending announcements."
  "autoscale-announcements")

(def ^:private dev-account-id
  "The `dev` account ID."
  (env :aws-dev-account-id))

(def ^:private prod-account-id
  "The `prod` account ID."
  (env :aws-prod-account-id))

(def ^:private account-ids
  "Our map of account IDs by account name keyword."
  {:dev dev-account-id
   :prod prod-account-id})

(def ^:private dev-autoscaling-topic-arn
  "The `dev` autoscaling topic ARN"
  (env :aws-dev-autoscaling-topic-arn))

(def ^:private prod-autoscaling-topic-arn
  "The `prod` autoscaling topic ARN"
  (env :aws-prod-autoscaling-topic-arn))

(def ^:private autoscaling-topics
  "Our map of autoscaling topics by account name keyword."
  {:dev dev-autoscaling-topic-arn
   :prod prod-autoscaling-topic-arn})

(def ^:private prod-role-arn
  "The ARN of the `prod` role we want to assume."
  (env :aws-prod-role-arn))

(defn- environment-to-account*
  "Get the account name keyword we should use for an environment. We'll default to `:dev` in the event of not knowing."
  [environment]
  (keyword (:account (onix/environment environment) "dev")))

(def environment-to-account
  (if (env :disable-caching)
    environment-to-account*
    (memo/ttl environment-to-account* :ttl/threshold (* 1000 60 15))))

(defn use-current-role?
  "Whether we should use the current IAM role or should assume a role in another account."
  [environment-name]
  (when-let [environment (onix/environment environment-name)]
    (not= "prod" (get-in environment [:metadata :account]))))

(defn alternative-credentials-if-necessary
  "Attempts to assume a role, if necessary, returning the credentials or nil if current role is to be used."
  [environment]
  (if-not (use-current-role? environment)
    (:credentials (sts/assume-role {:role-arn prod-role-arn :role-session-name "exploud"}))))

(def ^:private proxy-details
  (let [proxy-host (env :aws-proxy-host)
        proxy-port (env :aws-proxy-port)]
    (when (and proxy-host proxy-port)
      {:client-config {:proxy-host proxy-host
                       :proxy-port (Integer/valueOf proxy-port)}})))

(defn config
  [environment region]
  (merge (alternative-credentials-if-necessary environment)
         {:endpoint region}
         proxy-details))

(defn account-id
  "Get the account ID we should use for an environment."
  [environment]
  (get account-ids (environment-to-account environment)))

(defn autoscaling-topic
  "Get the autoscaling topic ARN we should use for an environment. We'll default ot whatever `:poke` uses in the event of not knowing."
  [environment]
  (get autoscaling-topics (environment-to-account environment)))

(defn announcement-queue-url
  "Create the URL for an announcement queue in a region and for an environment."
  [region environment]
  (format "https://%s.queue.amazonaws.com/%s/%s" region (account-id environment) autoscale-queue-name))

(defn asg-created-message
  "Create the message describing the creation of an ASG."
  [asg-name]
  (json/generate-string {:Message (json/generate-string {:Event "autoscaling:ASG_LAUNCH" :AutoScalingGroupName asg-name})}))

(defn asg-deleted-message
  "Create the message describing the deletion of an ASG."
  [asg-name]
  (json/generate-string {:Message (json/generate-string {:Event "autoscaling:ASG_TERMINATE" :AutoScalingGroupName asg-name})}))

(defn transform-instance-description
  "Takes an aws instance description and returns the fields we are interested in flattened"
  [{:keys [tags instance-id image-id private-ip-address launch-time]}]
  {:name (or (some (fn [{k :key v :value}] (when (= k "Name") v)) tags) "none")
   :instance-id instance-id
   :image-id image-id
   :private-ip private-ip-address
   :launch-time launch-time})

(defn map-by-instance-id
  [registrations]
  (apply merge (map (fn [[k v]] {(:instanceid v) (merge v {:numel-id (name k)})}) registrations)))

(defn add-numel-information
  [registrations v]
  (let [instance-id (:instance-id v)]
    (assoc v :numel-id (:numel-id (get registrations instance-id)))))

(defn describe-instances
  "Returns a json object describing the instances in the supplied environment
   with the given name and optional state (defaults to running)"
  [environment region name state]
  (let [pattern-name (or (and (str/blank? name) "*")
                         (and (.contains name "*") name)
                         (str name "-*"))
        state (or state "running")
        config (config environment region)
        registrations (when name (map-by-instance-id (numel/application-registrations environment name)))]
    (->> (ec2/describe-instances config
                                 :filters [{:name "tag:Name" :values [pattern-name]}
                                           {:name "instance-state-name" :values [state]}])
         :reservations
         (mapcat :instances)
         (map transform-instance-description)
         (map (partial add-numel-information registrations)))))

(defn describe-instances-plain
  "Returns a column formatted string describing the instances in the supplied environment
   with the given name and optional state (defaults to running)"
  [environment region name state]
  (util/as-table [:name :instance-id :image-id :launch-time :numel-id :private-ip] (describe-instances environment region name state)))

(def auto-scaling-groups
  (fn [environment region]
    (let [config (config environment region)]
      (loop [t nil gs []]
        (let [{:keys [next-token auto-scaling-groups]} (if t (auto/describe-auto-scaling-groups config :next-token t) (auto/describe-auto-scaling-groups config))]
          (if-not next-token
            (apply conj gs auto-scaling-groups)
            (recur next-token (apply conj gs auto-scaling-groups))))))))

(defn last-application-auto-scaling-group
  [application environment region]
  (let [asgs (auto-scaling-groups environment region)
        asg-names (map :auto-scaling-group-name asgs)
        pattern (re-pattern (str "^" application "-" environment))
        last-name (last (sort (filter #(re-find pattern %) asg-names)))]
    (first (filter #(= last-name (:auto-scaling-group-name %)) asgs))))

(defn auto-scaling-group
  [auto-scaling-group-name environment region]
  (first (:auto-scaling-groups (auto/describe-auto-scaling-groups (config environment region)
                                                                  :auto-scaling-group-names [auto-scaling-group-name]
                                                                  :max-records 1))))

(defn auto-scaling-group-instances
  [auto-scaling-group-name environment region]
  (:instances (auto-scaling-group auto-scaling-group-name environment region)))

(defn launch-configuration
  [launch-configuration-name environment region]
  (first (:launch-configurations (auto/describe-launch-configurations (config environment region)
                                                                     :launch-configuration-names [launch-configuration-name]))))

(defn image
  [image-id environment region]
  (first (:images (ec2/describe-images (config environment region)
                                       :image-ids [image-id]))))

(defn security-groups
  [environment region]
  (:security-groups (ec2/describe-security-groups (config environment region))))

(defn security-groups-by-id
  [environment region]
  (util/map-by-property :group-id (security-groups environment region)))

(defn security-groups-by-name
  [environment region]
  (util/map-by-property :group-name (security-groups environment region)))

(defn load-balancers
  [environment region]
  (:load-balancer-descriptions (elb/describe-load-balancers (config environment region))))

(defn load-balancer
  [environment region load-balancer-name]
  (first (:load-balancer-descriptions (elb/describe-load-balancers (config environment region)
                                                                   :load-balancer-names [load-balancer-name]))))

(defn load-balancer-instances
  [environment region load-balancer-name]
  (:instances (load-balancer environment region load-balancer-name)))

(defn load-balancers-with-names
  [environment region names]
  (when (seq names)
    (let [load-balancers-by-name (util/map-by-property :load-balancer-name (load-balancers environment region))]
      (apply merge (map (fn [n] {n (get load-balancers-by-name n)}) names)))))

(defn load-balancer-health
  [environment region load-balancer-name]
  (:instance-states (elb/describe-instance-health (config environment region) :load-balancer-name load-balancer-name)))

(defn- metadata-from-tag
  [subnet]
  (try
    (json/decode (:value (get (util/map-by-property :key (:tags subnet)) "immutable_metadata")) true)
    (catch Exception e
      (with-logging-context {:subnet-id (:subnet-id subnet)}
       (log/warn e "Failed to parse immutable_metadata for subnet"))
      nil)))

(defn- has-purpose?
  [purpose subnet]
  (let [metadata (metadata-from-tag subnet)]
    (= (:purpose metadata) purpose)))

(defn subnets-by-purpose
  [environment region purpose]
  (seq (filter (partial has-purpose? purpose) (:subnets (ec2/describe-subnets (config environment region))))))

(defn- has-zone?
  [availability-zones subnet]
  (let [zones (apply hash-set availability-zones)
        zone (:availability-zone subnet)]
    (contains? zones zone)))

(defn filter-by-availability-zones
  [availability-zones subnets]
  (if-not (seq availability-zones)
    subnets
    (seq (filter (partial has-zone? availability-zones) subnets))))

(defn instances
  [environment region instance-ids]
  (flatten (map :instances (flatten (:reservations (ec2/describe-instances (config environment region)
                                                                           :instance-ids (vec instance-ids)))))))
