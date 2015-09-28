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

(defn should-pause-because-of-deployment-params?
  [{:keys [action parameters]}]
  (cond (= action :maestro.messages.health/wait-for-instances-to-be-healthy)
        (get-in parameters [:new-state :tyranitar :deployment-params :pause-after-instances-healthy])
        (= action :maestro.messages.health/wait-for-load-balancers-to-be-healthy)
        (get-in parameters [:new-state :tyranitar :deployment-params :pause-after-load-balancers-healthy])
        (= action :maestro.messages.asg/deregister-old-instances-from-load-balancers)
        (get-in parameters [:new-state :tyranitar :deployment-params :pause-after-deregister-old-instances])
        :else false))

(defn should-pause?
  [message]
  (or (should-pause-because-told-to? message)
      (should-pause-because-of-deployment-params? message)))

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
             end-deployment-if-allowed
             :result)
        (do
          (log/write "Unknown action.")
          (capped-retry-after 5000 attempt 10))))))
