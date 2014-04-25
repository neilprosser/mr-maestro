(ns exploud.actions
  (:require [exploud.messages
             [asg :as asg]
             [data :as data]
             [health :as health]
             [notification :as notification]]
            [linked.set :refer [linked-set]]))

(def ^:private action-ordering
  (linked-set :exploud.messages.data/start-deployment-preparation
              :exploud.messages.data/validate-region
              :exploud.messages.data/validate-environment
              :exploud.messages.data/validate-application
              :exploud.messages.data/validate-user
              :exploud.messages.data/validate-image
              :exploud.messages.data/validate-message
              :exploud.messages.data/get-onix-metadata
              :exploud.messages.data/ensure-tyranitar-hash
              :exploud.messages.data/verify-tyranitar-hash
              :exploud.messages.data/get-tyranitar-application-properties
              :exploud.messages.data/get-tyranitar-deployment-params
              :exploud.messages.data/get-tyranitar-launch-data
              :exploud.messages.data/populate-previous-state
              :exploud.messages.data/populate-previous-tyranitar-application-properties
              :exploud.messages.data/get-previous-image-details
              :exploud.messages.data/create-names
              :exploud.messages.data/get-image-details
              :exploud.messages.data/verify-image
              :exploud.messages.data/check-contact-property
              :exploud.messages.data/check-shuppet-configuration
              :exploud.messages.data/add-required-security-groups
              :exploud.messages.data/map-security-group-ids
              :exploud.messages.data/verify-load-balancers
              :exploud.messages.data/populate-subnets
              :exploud.messages.data/populate-vpc-zone-identifier
              :exploud.messages.data/populate-availability-zones
              :exploud.messages.data/populate-termination-policies
              :exploud.messages.data/create-auto-scaling-group-tags
              :exploud.messages.data/generate-user-data
              :exploud.messages.data/complete-deployment-preparation
              :exploud.messages.data/start-deployment
              :exploud.messages.asg/create-launch-configuration
              :exploud.messages.asg/create-auto-scaling-group
              :exploud.messages.asg/disable-adding-instances
              :exploud.messages.asg/add-scaling-notifications
              :exploud.messages.asg/notify-of-auto-scaling-group-creation
              :exploud.messages.asg/resize-auto-scaling-group
              :exploud.messages.asg/wait-for-instances-to-exist
              :exploud.messages.asg/wait-for-instances-to-be-in-service
              :exploud.messages.asg/disable-instance-launching
              :exploud.messages.asg/disable-instance-termination
              :exploud.messages.health/wait-for-instances-to-be-healthy
              :exploud.messages.asg/enable-instance-launching
              :exploud.messages.asg/enable-instance-termination
              :exploud.messages.asg/enable-adding-instances
              :exploud.messages.asg/register-instances-with-load-balancers
              :exploud.messages.health/wait-for-load-balancers-to-be-healthy
              :exploud.messages.asg/disable-old-instance-launching
              :exploud.messages.asg/disable-old-instance-termination
              :exploud.messages.asg/disable-old-adding-instances
              :exploud.messages.asg/deregister-old-instances-from-load-balancers
              :exploud.messages.asg/notify-of-auto-scaling-group-deletion
              :exploud.messages.asg/delete-old-auto-scaling-group
              :exploud.messages.asg/wait-for-old-auto-scaling-group-deletion
              :exploud.messages.asg/delete-old-launch-configuration
              :exploud.messages.notification/send-completion-notification
              :exploud.messages.data/complete-deployment))

(defn to-function
  [action]
  (when action
    (let [namespace (namespace action)
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
  (count (take-while #(not= % action) action-ordering)))

(defn resume-action
  [tasks]
  (let [last-task (last tasks)
        last-action (keyword (:action last-task))]
    (if (= "running" (:status last-task))
      last-action
      (action-after last-action))))
