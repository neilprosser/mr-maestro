(ns exploud.messages.asg
  (:require [amazonica.aws
             [autoscaling :as auto]
             [elasticloadbalancing :as elb]
             [sqs :as sqs]]
            [clojure
             [set :as set]
             [string :as str]]
            [exploud
             [aws :as aws]
             [log :as log]
             [responses :refer :all]
             [util :as util]]
            [ring.util.codec :refer [base64-encode]]))

(def ^:private default-key-name
  "exploud")

(def ^:private default-wait-for-instances-to-exist-attempts
  50)

(def ^:private default-wait-for-instances-to-be-in-service-attempts
  50)

(def ^:private default-auto-scaling-group-deletion-attempts
  100)

(defn create-launch-configuration
  [{:keys [parameters]}]
  (let [{:keys [application environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [image-details launch-configuration-name selected-security-group-ids tyranitar user-data]} state
        {:keys [deployment-params]} tyranitar
        {:keys [instance-type]} deployment-params
        image-id (:id image-details)]
    (if-not state
      (do
        (log/write (format "Skipping creation of launch configuration."))
        (success parameters))
      (try
        (if (aws/launch-configuration launch-configuration-name environment region)
          (do
            (log/write (format "Launch configuration '%s' already exists." launch-configuration-name))
            (success parameters))
          (do
            (log/write (format "Creating launch configuration '%s' with image '%s'." launch-configuration-name image-id))
            (auto/create-launch-configuration (aws/config environment region)
                                              :launch-configuration-name launch-configuration-name
                                              :iam-instance-profile application
                                              :image-id image-id
                                              :instance-type instance-type
                                              :key-name default-key-name
                                              :security-groups (vec selected-security-group-ids)
                                              :user-data (base64-encode (.getBytes user-data)))
            (success (assoc-in parameters [state-key :created] true))))
        (catch Exception e
          (error-with e))))))

(defn create-auto-scaling-group
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name auto-scaling-group-tags availability-zones launch-configuration-name termination-policies tyranitar vpc-zone-identifier]} state
        {:keys [deployment-params]} tyranitar
        {:keys [default-cooldown health-check-grace-period health-check-type selected-load-balancers]} deployment-params]
    (if-not state
      (do
        (log/write "Skipping creation of auto scaling group.")
        (success parameters))
      (try
        (if (aws/auto-scaling-group auto-scaling-group-name environment region)
          (do
            (log/write (format "Auto scaling group '%s' already exists." auto-scaling-group-name))
            (success parameters))
          (do
            (log/write (format "Creating auto scaling group '%s'." auto-scaling-group-name))
            (auto/create-auto-scaling-group (aws/config environment region)
                                            :auto-scaling-group-name auto-scaling-group-name
                                            :availability-zones (vec availability-zones)
                                            :default-cooldown default-cooldown
                                            :desired-capacity 0
                                            :health-check-grace-period health-check-grace-period
                                            :health-check-type health-check-type
                                            :launch-configuration-name launch-configuration-name
                                            :load-balancer-names (vec selected-load-balancers)
                                            :max-size 0
                                            :min-size 0
                                            :tags (vec auto-scaling-group-tags)
                                            :termination-policies (vec termination-policies)
                                            :vpc-zone-identifier vpc-zone-identifier)
            (success (assoc-in parameters [state-key :created] true))))
        (catch Exception e
          (error-with e))))))

(defn disable-adding-instances
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name]} state]
    (if-not state
      (do
        (log/write "Skipping disabling adding instances.")
        (success parameters))
      (try
        (log/write (format "Disabling adding instances to ELB for auto scaling group '%s'." auto-scaling-group-name))
        (auto/suspend-processes (aws/config environment region)
                                :auto-scaling-group-name auto-scaling-group-name
                                :scaling-processes ["AddToLoadBalancer"])
        (success parameters)
        (catch Exception e
          (error-with e))))))

(defn add-scaling-notifications
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name created]} state]
    (if-not created
      (do
        (log/write "Nothing was created so not adding scaling notifications.")
        (success parameters))
      (try
        (log/write (format "Adding scaling notifications to '%s'." auto-scaling-group-name))
        (auto/put-notification-configuration (aws/config environment region)
                                             :auto-scaling-group-name auto-scaling-group-name
                                             :notification-types ["autoscaling:EC2_INSTANCE_LAUNCH" "autoscaling:EC2_INSTANCE_TERMINATE"]
                                             :topic-arn (aws/autoscaling-topic environment))
        (success parameters)
        (catch Exception e
          (error-with e))))))

(defn notify-of-auto-scaling-group-creation
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name created]} state]
    (if-not created
      (do
        (log/write "Nothing was created so skipping notification.")
        (success parameters))
      (try
        (log/write (format "Notifying of creation of auto-scaling group '%s'." auto-scaling-group-name))
        (sqs/send-message (aws/config environment region)
                          :queue-url (aws/announcement-queue-url region environment)
                          :delay-seconds 0
                          :message-body (aws/asg-created-message auto-scaling-group-name))
        (success parameters)
        (catch Exception e
          (error-with e))))))

(defn resize-auto-scaling-group
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name created tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [desired-capacity max min]} deployment-params]
    (if-not created
      (do
        (log/write "Nothing was created so skipping auto scaling group resize.")
        (success parameters))
      (try
        (log/write (format "Resizing auto scaling group '%s' to min %d, max %d." auto-scaling-group-name min max))
        (auto/update-auto-scaling-group (aws/config environment region)
                                        :auto-scaling-group-name auto-scaling-group-name
                                        :desired-capacity desired-capacity
                                        :max-size max
                                        :min-size min)
        (success parameters)
        (catch Exception e
          (error-with e))))))

(defn wait-for-instances-to-exist
  [{:keys [attempt parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [max min]} deployment-params
        max-attempts default-wait-for-instances-to-exist-attempts]
    (if-not state
      (do
        (log/write "No need to wait for instances to exist.")
        (success parameters))
      (try
        (let [instance-count (count (aws/auto-scaling-group-instances auto-scaling-group-name environment region))]
          (if (< instance-count min)
            (do
              (log/write (format "Auto scaling group '%s' has %d %s. Waiting for %d to exist." auto-scaling-group-name instance-count (util/pluralise instance-count "instance") min))
              (capped-retry-after 10000 attempt max-attempts))
            (do
              (log/write (format "Auto scaling group '%s' has instance count of %d which is between min %d and max %d." auto-scaling-group-name instance-count min max))
              (success parameters))))
        (catch Exception e
          (error-with e))))))

(defn wait-for-instances-to-be-in-service
  [{:keys [attempt parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [max min]} deployment-params
        max-attempts default-wait-for-instances-to-be-in-service-attempts]
    (if-not state
      (do
        (log/write "No need to wait for instances to be in service.")
        (success parameters))
      (try
        (let [instances (aws/auto-scaling-group-instances auto-scaling-group-name environment region)
              in-service-count (count (filter #(= "InService" (:lifecycle-state %)) instances))]
          (if (< in-service-count min)
            (do
              (when (= 1 attempt)
                (log/write (format "Waiting for at least %d %s to be InService in group '%s'." min (util/pluralise in-service-count "instance") auto-scaling-group-name)))
              (capped-retry-after 10000 attempt max-attempts))
            (do
              (log/write (format "Auto scaling group '%s' has %d InService %s which is between min %d and max %d." auto-scaling-group-name in-service-count (util/pluralise in-service-count "instance") min max))
              (success parameters))))
        (catch Exception e
          (error-with e))))))

(defn disable-instance-launching
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name]} state]
    (if-not state
      (do
        (log/write "No need to disable instance launching.")
        (success parameters))
      (try
        (log/write (format "Disabling new instance launching for auto scaling group '%s'." auto-scaling-group-name))
        (auto/suspend-processes (aws/config environment region)
                                :auto-scaling-group-name auto-scaling-group-name
                                :scaling-processes ["Launch"])
        (success parameters)
        (catch Exception e
          (error-with e))))))

(defn disable-instance-termination
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name]} state]
    (if-not state
      (do
        (log/write "No need to disable instance termination.")
        (success parameters))
      (try
        (log/write (format "Disabling instance termination for auto scaling group '%s'." auto-scaling-group-name))
        (auto/suspend-processes (aws/config environment region)
                                :auto-scaling-group-name auto-scaling-group-name
                                :scaling-processes ["Terminate"])
        (success parameters)
        (catch Exception e
          (error-with e))))))

(defn enable-instance-launching
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name]} state]
    (if-not state
      (do
        (log/write "No need to enable instance launching.")
        (success parameters))
      (try
        (log/write (format "Enabling new instance launching for auto scaling group '%s'." auto-scaling-group-name))
        (auto/resume-processes (aws/config environment region)
                               :auto-scaling-group-name auto-scaling-group-name
                               :scaling-processes ["Launch"])
        (success parameters)
        (catch Exception e
          (error-with e))))))

(defn enable-instance-termination
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name]} state]
    (if-not state
      (do
        (log/write "No need to enable instance termination.")
        (success parameters))
      (try
        (log/write (format "Enabling instance termination for auto scaling group '%s'." auto-scaling-group-name))
        (auto/resume-processes (aws/config environment region)
                               :auto-scaling-group-name auto-scaling-group-name
                               :scaling-processes ["Terminate"])
        (success parameters)
        (catch Exception e
          (error-with e))))))

(defn enable-adding-instances
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name]} state]
    (if-not state
      (do
        (log/write "No need to enable adding instances.")
        (success parameters))
      (try
        (log/write (format "Enabling adding instances to ELB for auto scaling group '%s'." auto-scaling-group-name))
        (auto/resume-processes (aws/config environment region)
                               :auto-scaling-group-name auto-scaling-group-name
                               :scaling-processes ["AddToLoadBalancer"])
        (success parameters)
        (catch Exception e
          (error-with e))))))

(defn register-instances-with-load-balancers
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [selected-load-balancers]} deployment-params]
    (if-not state
      (do
        (log/write "No need to register instances with load balancers.")
        (success parameters))
      (if-not (seq selected-load-balancers)
        (do
          (log/write "No load balancers specified.")
          (success parameters))
        (try
          (let [instances (seq (aws/auto-scaling-group-instances auto-scaling-group-name environment region))
                instance-count (count instances)
                instance-ids (map :instance-id instances)]
            (if-not instances
              (log/write "No instances to register.")
              (doseq [lb selected-load-balancers]
                (log/write (format "Registering %s [%s] with load balancer %s." (util/pluralise instance-count "instance") (str/join ", " instance-ids) lb))
                (elb/register-instances-with-load-balancer (aws/config environment region)
                                                           :load-balancer-name lb
                                                           :instances (vec (map (fn [i] {:instance-id i}) instance-ids)))))
            (success parameters))
          (catch Exception e
            (error-with e)))))))

(defn add-scheduled-actions
  [{:keys [parameters] :as message}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [scheduled-actions]} deployment-params]
    (if-not state
      (do
        (log/write "No need to add scheduled actions.")
        (success parameters))
      (try
        (if-not (seq scheduled-actions)
          (do
            (log/write "No scheduled actions to add.")
            (success parameters))
          (do
            (doseq [scheduled-action scheduled-actions]
              (let [[action-name description] scheduled-action
                    {:keys [cron desired-capacity max min]} description
                    nice-action-name (name action-name)
                    full-name (str/join "-" [auto-scaling-group-name nice-action-name])]
                (log/write (format "Adding scheduled action '%s'." nice-action-name))
                (auto/put-scheduled-update-group-action (aws/config environment region)
                                                        :auto-scaling-group-name auto-scaling-group-name
                                                        :desired-capacity desired-capacity
                                                        :max-size max
                                                        :min-size min
                                                        :recurrence cron
                                                        :scheduled-action-name full-name)))
            (success parameters)))
        (catch Exception e
          (error-with e))))))

(defn disable-old-instance-launching
  [{:keys [parameters] :as message}]
  (let [{:keys [environment region]} parameters
        state-key (util/previous-state-key parameters)
        state (state-key parameters)]
    (if-let [old-auto-scaling-group-name (:auto-scaling-group-name state)]
      (try
        (disable-instance-launching (assoc message :parameters {:environment environment
                                                                :region region
                                                                (util/new-state-key parameters) {:auto-scaling-group-name old-auto-scaling-group-name}}))
        (success parameters)
        (catch Exception e
          (error-with e)))
      (success parameters))))

(defn disable-old-instance-termination
  [{:keys [parameters] :as message}]
  (let [{:keys [environment region]} parameters
        state-key (util/previous-state-key parameters)
        state (state-key parameters)]
    (if-let [old-auto-scaling-group-name (:auto-scaling-group-name state)]
      (try
        (disable-instance-termination (assoc message :parameters {:environment environment
                                                                  :region region
                                                                  (util/new-state-key parameters) {:auto-scaling-group-name old-auto-scaling-group-name}}))
        (success parameters)
        (catch Exception e
          (error-with e)))
      (success parameters))))

(defn disable-old-adding-instances
  [{:keys [parameters] :as message}]
  (let [{:keys [environment region]} parameters
        state-key (util/previous-state-key parameters)
        state (state-key parameters)]
    (if-let [old-auto-scaling-group-name (:auto-scaling-group-name state)]
      (try
        (disable-adding-instances (assoc message :parameters {:environment environment
                                                              :region region
                                                              (util/new-state-key parameters) {:auto-scaling-group-name old-auto-scaling-group-name}}))
        (success parameters)
        (catch Exception e
          (error-with e)))
      (success parameters))))

(defn deregister-old-instances-from-load-balancers
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/previous-state-key parameters)
        state (state-key parameters)
        {:keys [tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [selected-load-balancers]} deployment-params]
    (if-let [old-auto-scaling-group-name (:auto-scaling-group-name state)]
      (if-not (seq selected-load-balancers)
        (do
          (log/write "No load balancers specified.")
          (success parameters))
        (try
          (let [instances (seq (aws/auto-scaling-group-instances old-auto-scaling-group-name environment region))
                instance-count (count instances)
                instance-ids (apply hash-set (map :instance-id instances))]
            (if-not instances
              (log/write "No instances to deregister.")
              (doseq [lb selected-load-balancers]
                (let [load-balancer-instances (aws/load-balancer-instances environment region lb)
                      lb-instance-ids (apply hash-set (map :instance-id load-balancer-instances))
                      present-instance-ids (set/intersection lb-instance-ids instance-ids)]
                  (when (seq present-instance-ids)
                    (log/write (format "Deregistering %s [%s] from load balancer %s." (util/pluralise (count present-instance-ids) "instance") (str/join ", " present-instance-ids) lb))
                    (elb/deregister-instances-from-load-balancer (aws/config environment region)
                                                                 :load-balancer-name lb
                                                                 :instances (vec (map (fn [i] {:instance-id i}) present-instance-ids)))))))
            (success parameters))
          (catch Exception e
            (error-with e))))
      (success parameters))))

(defn notify-of-auto-scaling-group-deletion
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/previous-state-key parameters)
        state (state-key parameters)]
    (if-let [old-auto-scaling-group-name (:auto-scaling-group-name state)]
      (try
        (log/write (format "Notifying of deletion of auto-scaling group '%s'." old-auto-scaling-group-name))
        (sqs/send-message (aws/config environment region)
                          :queue-url (aws/announcement-queue-url region environment)
                          :delay-seconds 0
                          :message-body (aws/asg-deleted-message old-auto-scaling-group-name))
        (success parameters)
        (catch Exception e
          (error-with e)))
      (success parameters))))

(defn delete-old-auto-scaling-group
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/previous-state-key parameters)
        state (state-key parameters)]
    (if-let [old-auto-scaling-group-name (:auto-scaling-group-name state)]
      (try
        (if (aws/auto-scaling-group old-auto-scaling-group-name environment region)
          (do
            (log/write (format "Deleting auto scaling group '%s'." old-auto-scaling-group-name))
            (auto/delete-auto-scaling-group (aws/config environment region)
                                            :auto-scaling-group-name old-auto-scaling-group-name
                                            :force-delete true))
          (log/write (format "Auto scaling group '%s' has already been deleted." old-auto-scaling-group-name)))
        (success parameters)
        (catch Exception e
          (error-with e)))
      (success parameters))))

(defn wait-for-old-auto-scaling-group-deletion
  [{:keys [attempt parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/previous-state-key parameters)
        state (state-key parameters)
        {:keys [tyranitar]} state
        {:keys [deployment-params]} tyranitar
        max-attempts (get deployment-params :auto-scaling-group-deletion-attempts default-auto-scaling-group-deletion-attempts)]
    (if-let [old-auto-scaling-group-name (:auto-scaling-group-name state)]
      (try
        (if (aws/auto-scaling-group old-auto-scaling-group-name environment region)
          (do
            (when (= 1 attempt)
              (log/write (format "Waiting for auto scaling group '%s' to be deleted." old-auto-scaling-group-name)))
            (capped-retry-after 10000 attempt max-attempts))
          (do
            (log/write (format "Finished deletion of auto scaling group '%s'." old-auto-scaling-group-name))
            (success parameters)))
        (catch Exception e
          (error-with e)))
      (success parameters))))

(defn delete-old-launch-configuration
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/previous-state-key parameters)
        state (state-key parameters)]
    (if-let [old-launch-configuration-name (:launch-configuration-name state)]
      (try
        (if (aws/launch-configuration old-launch-configuration-name environment region)
          (do
            (log/write (format "Deleting launch configuration '%s'." old-launch-configuration-name))
            (auto/delete-launch-configuration (aws/config environment region)
                                              :launch-configuration-name old-launch-configuration-name))
          (log/write (format "Launch configuration '%s' has already been deleted." old-launch-configuration-name)))
        (success parameters)
        (catch Exception e
          (error-with e)))
      (success parameters))))

(defn scale-down-after-deployment
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name created tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [scale-down-after-deployment]} deployment-params]
    (if-not scale-down-after-deployment
      (success parameters)
      (try
        (log/write (format "Resizing auto scaling group '%s' to min 0, max 0." auto-scaling-group-name))
        (auto/update-auto-scaling-group (aws/config environment region)
                                        :auto-scaling-group-name auto-scaling-group-name
                                        :desired-capacity 0
                                        :max-size 0
                                        :min-size 0)
        (success parameters)
        (catch Exception e
          (error-with e))))))
