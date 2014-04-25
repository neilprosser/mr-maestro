(ns exploud.messages
  (:require [clj-time.core :as time]
            [exploud
             [actions :as actions]
             [bindings :refer :all]
             [deployments :as deployments]
             [elasticsearch :as es]
             [log :as log]
             [responses :refer :all]
             [tasks :as tasks]
             [util :as util]]))

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

(defn successful?
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

(defn ensure-task
  [{:keys [attempt message] :as details}]
  (let [{:keys [action]} message]
    (when (= 1 attempt)
      (es/create-task *task-id* *deployment-id* {:action action
                                                 :sequence (actions/sequence-number action)
                                                 :start (time/now)
                                                 :status "running"})))
  details)

(defn perform-action
  [action-fn details]
  (try
    (let [result (action-fn (rewrap details))]
      (assoc details :result result))
    (catch Exception e
      (assoc details :result (error-with e)))))

(defn log-error-if-necessary
  [{:keys [result] :as details}]
  (if (= (:status result) :error)
    (if-let [throwable (:throwable result)]
      (log/write (.getMessage throwable))
      (log/write "An unspecified error has occurred. It might be worth checking Exploud's logs.")))
  details)

(defn determine-next-action
  [{:keys [message] :as details}]
  (let [{:keys [action]} message]
    (assoc details :next-action (actions/action-after action))))

(defn update-task
  [{:keys [message result] :as details}]
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

(defn should-pause-because-told-to?
  [details]
  false)

(defn should-pause-because-of-deployment-params?
  [{:keys [action message next-action]}]
  (when (= next-action ))
  (get-in message [:parameters :new-state :tyranitar :deployment-params :pause-after-healthy]))

(defn should-pause?
  [{:keys [next-action] :as details}]
  (or (should-pause-because-told-to? details)
      (should-pause-because-of-deployment-params? details)))

(defn enqueue-next-task
  [{:keys [message next-action result] :as details}]
  (when (and (successful? result)
             next-action)
    (let [{:keys [parameters]} message]
      (if-not (should-pause? parameters)
        (tasks/enqueue {:action next-action
                        :parameters parameters})
        (do
          (log/write "Pausing deployment")
          (deployments/pause parameters)))))
  details)

(defn is-finishing?
  [{:keys [next-action]}]
  (not next-action))

(defn is-safely-failed?
  [{:keys [message]}]
  (= "invalid" (get-in message [:parameters :status])))

(defn unlock-deployment-if-allowed
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
      (if-let [action-fn (actions/to-function action)]
        (->> details
             ensure-task
             (perform-action action-fn)
             log-error-if-necessary
             determine-next-action
             update-task
             update-deployment
             enqueue-next-task
             unlock-deployment-if-allowed
             :result)
        (error-with (ex-info "Unknown action" {:type ::unknown-action
                                               :action action}))))))
