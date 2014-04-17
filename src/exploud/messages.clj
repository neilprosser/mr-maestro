(ns exploud.messages
  (:require [clj-time.core :as time]
            [exploud
             [bindings :refer :all]
             [deployments :as deployments]
             [elasticsearch :as es]
             [responses :refer :all]
             [tasks :as tasks]
             [util :as util]]
            [exploud.messages
             [asg :as asg]
             [data :as data]
             [health :as health]]
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
              :exploud.messages.data/complete-deployment))

(defn- to-function
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

(defn- rewrap
  [{:keys [mid message attempt]}]
  (let [{:keys [parameters]} message]
    {:id mid
     :parameters (assoc parameters :id *deployment-id* :status "running")
     :attempt attempt}))

(defn- terminal?
  [{:keys [status]}]
  (or (= status :error)
      (= status :success)))

(defn- successful?
  [{:keys [status]}]
  (= :success status))

(defn- task-status-for
  [{:keys [status]}]
  (case status
    :success "completed"
    :error "failed"
    "running"))

(defn- deployment-status-for
  [{:keys [status]}]
  (case status
    :error "failed"
    "running"))

(defn- ensure-task
  [{:keys [attempt message] :as details}]
  (let [{:keys [action]} message]
    (when (= 1 attempt)
      (es/create-task *task-id* *deployment-id* {:action action
                                                 :sequence (count (take-while #(not= % action) action-ordering))
                                                 :start (time/now)
                                                 :status "running"})))
  details)

(defn- perform-action
  [action-fn details]
  (try
    (let [result (action-fn (rewrap details))]
      (assoc details :result result))
    (catch Exception e
      (assoc details :result (error-with e)))))

(defn- determine-next-action
  [{:keys [message] :as details}]
  (let [{:keys [action]} message]
    (assoc details :next-action (action-after action))))

(defn- update-task
  [{:keys [message result] :as details}]
  (when (terminal? result)
    (es/update-task *task-id* *deployment-id* {:end (time/now)
                                               :status (task-status-for result)}))
  details)

(defn- failure-status
  [{:keys [message]}]
  (let [{:keys [parameters]} message]
    (if (= "preparation" (:phase parameters))
      "invalid"
      "failed")))

(defn- update-deployment
  [{:keys [message result] :as details}]
  (case (:status result)
    :success (let [status (if (:next-action details) "running" "completed")
                   new-parameters (assoc (:parameters result) :status status)]
               (es/upsert-deployment *deployment-id* new-parameters)
               (assoc-in details [:message :parameters] new-parameters))
    :error (let [new-parameters (assoc (:parameters message)
                                  :end (time/now)
                                  :status (failure-status details))]
             (es/upsert-deployment *deployment-id* new-parameters)
             (assoc-in details [:message :parameters] new-parameters))
    :retry details))

(defn- enqueue-next-task
  [{:keys [message next-action result] :as details}]
  (when (and (successful? result)
             next-action)
    (let [{:keys [parameters]} message]
      (tasks/enqueue {:action next-action
                      :parameters parameters})))
  details)

(defn- is-finishing?
  [{:keys [next-action]}]
  (not next-action))

(defn- is-safely-failed?
  [{:keys [message]}]
  (= "invalid" (get-in message [:parameters :status])))

(defn- unlock-deployment-if-allowed
  [{:keys [message] :as details}]
  (if (or (is-finishing? details)
          (is-safely-failed? details))
    (let [{:keys [parameters]} message]
      (deployments/end parameters)))
  details)

(defn handler
  [{:keys [mid message attempt] :as details}]
  (let [{:keys [action parameters]} message
        {:keys [id]} parameters]
    (when-not id
      (throw (ex-info "No deployment ID provided" {:type ::missing-deployment-id})))
    (binding [*task-id* mid
              *deployment-id* id]
      (if-let [action-fn (to-function action)]
        (->> details
             ensure-task
             (perform-action action-fn)
             determine-next-action
             update-task
             update-deployment
             enqueue-next-task
             unlock-deployment-if-allowed
             :result)
        (error-with (ex-info "Unknown action" {:type ::unknown-action
                                               :action action}))))))
