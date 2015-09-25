(ns maestro.deployments
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [maestro
             [actions :as actions]
             [elasticsearch :as es]
             [log :as log]
             [redis :as redis]]))

(def ^:private retryable-tasks
  #{:maestro.messages.asg/wait-for-instances-to-exist
    :maestro.messages.asg/wait-for-instances-to-be-in-service
    :maestro.messages.health/wait-for-instances-to-be-healthy
    :maestro.messages.health/wait-for-load-balancers-to-be-healthy
    :maestro.messages.asg/wait-for-old-auto-scaling-group-deletion})

(defn- validate-retryable-tasks
  []
  (let [difference (clojure.set/difference retryable-tasks (set @#'maestro.actions/action-ordering))]
    (when-not (zero? (count difference))
      (throw (RuntimeException. (format "Unknown action(s): %s" (str/join "," difference)))))))

(validate-retryable-tasks)

(defn locked?
  []
  (redis/locked?))

(defn lock
  []
  (redis/lock))

(defn unlock
  []
  (redis/unlock))

(defn paused?
  [{:keys [application environment region]}]
  (redis/paused? application environment region))

(defn paused
  []
  (redis/paused))

(defn register-pause
  [{:keys [application environment region]}]
  (redis/register-pause application environment region))

(defn unregister-pause
  [{:keys [application environment region]}]
  (redis/unregister-pause application environment region))

(defn pause-registered?
  [{:keys [application environment region]}]
  (redis/pause-registered? application environment region))

(defn awaiting-pause
  []
  (redis/awaiting-pause))

(defn register-cancel
  [{:keys [application environment region]}]
  (redis/register-cancel application environment region))

(defn unregister-cancel
  [{:keys [application environment region]}]
  (redis/unregister-cancel application environment region))

(defn cancel-registered?
  [{:keys [application environment region]}]
  (redis/cancel-registered? application environment region))

(defn awaiting-cancel
  []
  (redis/awaiting-cancel))

(defn in-progress?
  [{:keys [application environment region]}]
  (redis/in-progress? application environment region))

(defn in-progress
  []
  (redis/in-progress))

(defn stopped-on-retryable-task?
  [{:keys [application environment region]}]
  (when-let [{:keys [id]} (es/last-failed-deployment {:application application
                                                      :environment environment
                                                      :region region})]
    (when-let [{:keys [action status]} (last (es/deployment-tasks id))]
      (and (contains? retryable-tasks action)
           (= status "failed")))))

(defn can-retry?
    [{:keys [application environment region] :as parameters}]
    (and (in-progress? parameters)
         (not (paused? parameters))
         (stopped-on-retryable-task? parameters)))

(defn begin
  [{:keys [application environment id region] :as deployment}]
  (if (redis/begin-deployment deployment)
    (let [updated-deployment (assoc deployment :start (time/now) :status "running")]
      (es/upsert-deployment id updated-deployment)
      (redis/enqueue {:action :maestro.messages.data/start-deployment-preparation
                      :parameters updated-deployment})
      deployment)
    (throw (ex-info "Deployment already in progress" {:type ::deployment-in-progress
                                                      :application application
                                                      :environment environment
                                                      :region region}))))

(defn end
  [parameters]
  (redis/end-deployment parameters)
  parameters)

(defn cancel
  [parameters]
  (redis/cancel-deployment parameters)
  parameters)

(defn undo
  [{:keys [application environment message region silent user] :as parameters}]
  (if-let [id (in-progress? parameters)]
    (if-let [deployment (es/deployment id)]
      (if (or (= "failed" (:status deployment))
              (paused? deployment))
        (let [updated-deployment (assoc deployment :status "running" :undo true :undo-message message :undo-silent silent :undo-user user)]
          (es/upsert-deployment id updated-deployment)
          (redis/resume application environment region)
          (redis/enqueue {:action :maestro.messages.data/start-deployment
                          :parameters updated-deployment})
          (:id updated-deployment))
        (throw (ex-info "Deployment has not failed or is not paused" {:type ::deployment-not-failed
                                                                      :id id})))
      (throw (ex-info "Deployment could not be found" {:type ::deployment-not-found
                                                       :id id})))
    (throw (ex-info "Deployment is not in progress" {:type ::deployment-not-in-progress
                                                     :application application
                                                     :environment environment
                                                     :region region}))))

(defn redeploy
  [{:keys [application environment id message region user]}]
  (if-let [previous (es/last-completed-deployment {:application application
                                                   :environment environment
                                                   :region region})]
    (begin {:application application
            :environment environment
            :id id
            :message message
            :new-state {:image-details {:id (get-in previous [:new-state :image-details :id])}}
            :region region
            :user user})
    (throw (ex-info "No previous completed deployment could be found" {:type ::previous-completed-deployment-not-found
                                                                       :application application
                                                                       :environment environment}))))

(defn rollback
  [{:keys [application environment id message region user]}]
  (if-let [previous (es/last-completed-deployment {:application application
                                                   :environment environment
                                                   :region region})]
    (begin {:application application
            :environment environment
            :id id
            :message message
            :new-state {:hash (get-in previous [:previous-state :hash])
                        :image-details {:id (get-in previous [:previous-state :image-details :id])}}
            :region region
            :rollback true
            :user user})
    (throw (ex-info "No previous completed deployment could be found" {:type ::previous-completed-deployment-not-found
                                                                       :application application
                                                                       :environment environment}))))

(defn pause
  [{:keys [application environment id region]}]
  (redis/unregister-pause application environment region)
  (redis/pause application environment id region))

(defn resume
  [{:keys [application environment region]}]
  (when-let [id (redis/in-progress? application environment region)]
    (let [deployment (es/deployment id)
          action (actions/resume-action (es/deployment-tasks id))]
      (log/write* id "Resuming deployment.")
      (redis/enqueue {:action action
                      :parameters deployment})
      (redis/resume application environment region))))

(defn retry
  [{:keys [application environment region] :as parameters}]
  (if-let [id (in-progress? parameters)]
    (if-let [deployment (es/deployment id)]
      (if-let [action (:action (last (es/deployment-tasks id)))]
        (let [updated-deployment (assoc deployment :status "running")]
          (log/write* id "Retrying deployment.")
          (es/upsert-deployment id updated-deployment)
          (redis/enqueue {:action action
                          :parameters updated-deployment}))
        (throw (ex-info "Unable to find last task" {:type ::last-task-not-found
                                                    :deployment-id id})))
      (throw (ex-info "Deployment could not be found" {:type ::deployment-not-found
                                                       :id id})))
    (throw (ex-info "Deployment is not in progress" {:type ::deployment-not-in-progress
                                                     :application application
                                                     :environment environment
                                                     :region region}))))
