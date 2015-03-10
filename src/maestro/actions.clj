(ns maestro.actions
  (:require [clojure.string :as str]
            [maestro.messages
             [asg :as asg]
             [data :as data]
             [health :as health]
             [notification :as notification]]))

(def ^:private action-ordering
  [:maestro.messages.data/start-deployment-preparation
   :maestro.messages.data/validate-deployment
   :maestro.messages.data/get-lister-metadata
   :maestro.messages.data/ensure-tyrant-hash
   :maestro.messages.data/verify-tyrant-hash
   :maestro.messages.data/get-tyrant-application-properties
   :maestro.messages.data/get-tyrant-deployment-params
   :maestro.messages.data/validate-deployment-params
   :maestro.messages.data/get-tyrant-launch-data
   :maestro.messages.data/populate-previous-state
   :maestro.messages.data/populate-previous-tyrant-application-properties
   :maestro.messages.data/get-previous-image-details
   :maestro.messages.data/create-names
   :maestro.messages.data/get-image-details
   :maestro.messages.data/verify-image
   :maestro.messages.data/check-for-embargo
   :maestro.messages.data/check-instance-type-compatibility
   :maestro.messages.data/check-contact-property
   :maestro.messages.data/check-pedantic-configuration
   :maestro.messages.data/create-block-device-mappings
   :maestro.messages.data/add-required-security-groups
   :maestro.messages.data/map-security-group-ids
   :maestro.messages.data/verify-load-balancers
   :maestro.messages.data/check-for-deleted-load-balancers
   :maestro.messages.data/populate-subnets
   :maestro.messages.data/populate-vpc-zone-identifier
   :maestro.messages.data/populate-termination-policies
   :maestro.messages.data/create-auto-scaling-group-tags
   :maestro.messages.data/generate-user-data
   :maestro.messages.data/complete-deployment-preparation
   :maestro.messages.notification/send-start-notification
   :maestro.messages.data/start-deployment
   :maestro.messages.asg/create-launch-configuration
   :maestro.messages.asg/create-auto-scaling-group
   :maestro.messages.asg/disable-adding-instances
   :maestro.messages.asg/add-scaling-notifications
   :maestro.messages.asg/notify-of-auto-scaling-group-creation
   :maestro.messages.asg/resize-auto-scaling-group
   :maestro.messages.asg/wait-for-instances-to-exist
   :maestro.messages.asg/wait-for-instances-to-be-in-service
   :maestro.messages.asg/disable-instance-launching
   :maestro.messages.asg/disable-instance-termination
   :maestro.messages.health/wait-for-instances-to-be-healthy
   :maestro.messages.asg/enable-instance-launching
   :maestro.messages.asg/enable-instance-termination
   :maestro.messages.asg/enable-adding-instances
   :maestro.messages.asg/register-instances-with-load-balancers
   :maestro.messages.health/wait-for-load-balancers-to-be-healthy
   :maestro.messages.asg/add-scheduled-actions
   :maestro.messages.asg/disable-old-instance-launching
   :maestro.messages.asg/disable-old-instance-termination
   :maestro.messages.asg/disable-old-adding-instances
   :maestro.messages.asg/deregister-old-instances-from-load-balancers
   :maestro.messages.asg/notify-of-auto-scaling-group-deletion
   :maestro.messages.asg/delete-old-auto-scaling-group
   :maestro.messages.asg/wait-for-old-auto-scaling-group-deletion
   :maestro.messages.asg/delete-old-launch-configuration
   :maestro.messages.asg/scale-down-after-deployment
   :maestro.messages.notification/send-completion-notification
   :maestro.messages.data/complete-deployment])

(defn- replace-legacy
  [action]
  (let [new-namespace (str/replace-first (namespace action) "exploud" "maestro")]
    (keyword new-namespace (name action))))

(defn to-function
  [action]
  (when action
    (let [namespace (namespace (replace-legacy action))
          name (name action)]
      (resolve (symbol (format "%s/%s" namespace name))))))

(defn- validate-action-keyword
  [action]
  (when-not (keyword? action)
    (throw (RuntimeException. (format "Not a keyword: %s" action))))
  (when-not (to-function action)
    (throw (RuntimeException. (format "Invalid task: %s" action)))))

(defn- validate-action-ordering
  []
  (doseq [a action-ordering]
    (validate-action-keyword a)))

(validate-action-ordering)

(defn action-after
  [action]
  (let [action-fn (to-function action)]
    (->> action-ordering
         (drop-while #(not= (to-function action) (to-function %)))
         fnext)))

(defn sequence-number
  [action]
  (count (take-while #(not= % (replace-legacy action)) action-ordering)))

(defn resume-action
  [tasks]
  (let [last-task (last tasks)
        last-action (keyword (:action last-task))]
    (if (= "running" (:status last-task))
      last-action
      (action-after last-action))))
