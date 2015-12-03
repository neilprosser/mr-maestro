(ns maestro.messages
  (:require [clj-time.core :as time]
            [clojure.tools.logging :refer [error]]
            [io.clj.logging :refer [with-logging-context]]
            [maestro
             [actions :as actions]
             [bindings :refer :all]
             [deployments :as deployments]
             [elasticsearch :as es]
             [log :as log]
             [redis :as redis]
             [responses :refer :all]
             [util :as util]]))

(defn rewrap
  [{:keys [mid message attempt]}]
  (let [{:keys [parameters]} message]
    {:id mid
     :parameters (assoc parameters :id *deployment-id* :status "running")
     :attempt attempt}))

(defn terminal?
  [{:keys [status]}]
  (or (= status :error)
      (= status :success)))

(defn successful?
  [{:keys [status]}]
  (= :success status))

(defn task-status-for
  [{:keys [status]}]
  (case status
    :success "completed"
    :error "failed"
    "running"))

(defn deployment-status-for
  [{:keys [status]}]
  (case status
    :error "failed"
    "running"))

(defn ensure-task
  [{:keys [attempt message] :as details}]
  (when (= 1 attempt)
    (let [{:keys [action]} message]
      (es/create-task *task-id* *deployment-id* {:action action
                                                 :sequence (actions/sequence-number action)
                                                 :start (time/now)
                                                 :status "running"})))
  details)

(defn perform-action
  [action-fn details]
  (try
    (let [rewrapped (rewrap details)]
      (if-let [result (action-fn rewrapped)]
        (assoc details :result result)
        (assoc details :result (success (:parameters rewrapped)))))
    (catch Exception e
      (assoc details :result (error-with e)))))

(defn log-error-if-necessary
  [{:keys [result] :as details}]
  (if (= (:status result) :error)
    (if-let [throwable (:throwable result)]
      (log/write (.getMessage throwable))
      (log/write "An unspecified error has occurred. It might be worth checking Maestro's logs.")))
  details)

(defn determine-next-action
  [{:keys [message] :as details}]
  (let [{:keys [action]} message]
    (assoc details :next-action (actions/action-after action))))

(defn should-cancel?
  [{:keys [message result]}]
  (and (= :retry (:status result))
       (deployments/cancel-registered? (:parameters message))))

(defn fail-if-cancelled
  [{:keys [message result] :as details}]
  (let [{:keys [parameters]} message]
    (if (should-cancel? details)
      (do
        (log/write "Cancelling deployment.")
        (deployments/cancel parameters)
        (assoc-in details [:result :status] :error))
      details)))

(defn update-task
  [{:keys [result] :as details}]
  (when (terminal? result)
    (es/update-task *task-id* *deployment-id* {:end (time/now)
                                               :status (task-status-for result)}))
  details)

(defn failure-status
  [{:keys [message]}]
  (let [{:keys [parameters]} message]
    (if (= "preparation" (:phase parameters))
      "invalid"
      "failed")))

(defn update-deployment
  [{:keys [message result] :as details}]
  (let [{:keys [status]} result]
    (try
      (case status
        :success (let [status (if (:next-action details) "running" "completed")
                       new-parameters (assoc (:parameters result) :status status)]
                   (es/upsert-deployment *deployment-id* new-parameters)
                   (assoc-in details [:message :parameters] new-parameters))
        :error (let [new-parameters (assoc (:parameters message)
                                           :end (time/now)
                                           :status (failure-status details))]
                 (es/upsert-deployment *deployment-id* new-parameters)
                 (assoc-in details [:message :parameters] new-parameters))
        :retry details)
      (catch IllegalArgumentException e
        (with-logging-context {:status status}
          (error e "Unknown result status"))
        details))))

(defn should-pause-because-told-to?
  [{:keys [parameters]}]
  (deployments/pause-registered? parameters))

(defn matches-action-and-has-parameter?
  [action parameters {:keys [desired-action parameter-path]}]
  (when (= action desired-action)
    (get-in parameters parameter-path)))

(def pause-deployment-params
  [{:desired-action :maestro.messages.health/wait-for-instances-to-be-healthy
    :parameter-path [:new-state :tyranitar :deployment-params :pause-after-instances-healthy]}
   {:desired-action :maestro.messages.health/wait-for-load-balancers-to-be-healthy
    :parameter-path [:new-state :tyranitar :deployment-params :pause-after-load-balancers-healthy]}
   {:desired-action :maestro.messages.asg/deregister-old-instances-from-load-balancers
    :parameter-path [:new-state :tyranitar :deployment-params :pause-after-deregister-old-instances]}])

(defn should-pause-because-of-deployment-params?
  [{:keys [action parameters]}]
  (some (partial matches-action-and-has-parameter? action parameters) pause-deployment-params))

(def instruction-params
  [{:desired-action :maestro.messages.data/start-deployment
    :parameter-path [:new-state :onix :instructions :beforeDeployment :message]}
   {:desired-action :maestro.messages.health/wait-for-instances-to-be-healthy
    :parameter-path [:new-state :onix :instructions :afterInstancesHealthy :message]}
   {:desired-action :maestro.messages.data/complete-deployment
    :parameter-path [:new-state :onix :instructions :afterDeployment :message]}])

(defn should-pause-because-of-instructions?
  [{:keys [action parameters]}]
  (some (partial matches-action-and-has-parameter? action parameters) instruction-params))

(defn should-pause?
  [message]
  (or (should-pause-because-told-to? message)
      (should-pause-because-of-deployment-params? message)
      (should-pause-because-of-instructions? message)))

(defn enqueue-next-task
  [{:keys [message next-action result] :as details}]
  (when (and (successful? result)
             next-action)
    (let [{:keys [parameters]} message]
      (if-not (should-pause? message)
        (redis/enqueue {:action next-action
                        :parameters parameters})
        (do
          (log/write "Pausing deployment.")
          (deployments/pause parameters)))))
  details)

(defn display-instruction-if-needed
  [{:keys [message] :as details}]
  (let [{:keys [action parameters]} message
        before-deployment (get-in parameters [:new-state :onix :instructions :beforeDeployment :message])
        after-instances-healthy (get-in parameters [:new-state :onix :instructions :afterInstancesHealthy :message])
        after-deployment (get-in parameters [:new-state :onix :instructions :afterDeployment :message])]
    (cond (and (= action :maestro.messages.data/start-deployment) before-deployment)
          (log/write before-deployment)
          (and (= action :maestro.messages.health/wait-for-instances-to-be-healthy) after-instances-healthy)
          (log/write after-instances-healthy)
          (and (= action :maestro.messages.data/complete-deployment) after-deployment)
          (log/write after-deployment)))
  details)

(defn finishing?
  [{:keys [next-action]}]
  (not next-action))

(defn safely-failed?
  [{:keys [message]}]
  (= "invalid" (get-in message [:parameters :status])))

(defn end-deployment-if-allowed
  [{:keys [message] :as details}]
  (if (or (finishing? details)
          (safely-failed? details))
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
      (if-let [action-fn (actions/to-function action)]
        (->> details
             ensure-task
             (perform-action action-fn)
             log-error-if-necessary
             determine-next-action
             fail-if-cancelled
             update-task
             update-deployment
             enqueue-next-task
             display-instruction-if-needed
             end-deployment-if-allowed
             :result)
        (do
          (log/write "Unknown action.")
          (capped-retry-after 5000 attempt 10))))))
