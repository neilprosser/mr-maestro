(ns maestro.deployments
  (:require [clj-time.core :as time]
            [maestro
             [actions :as actions]
             [elasticsearch :as es]
             [log :as log]
             [redis :as redis]]))

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

(defn in-progress?
  [{:keys [application environment region]}]
  (redis/in-progress? application environment region))

(defn in-progress
  []
  (redis/in-progress))

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
  (if-let [previous (first (es/get-deployments {:application application
                                                :environment environment
                                                :from 0
                                                :region region
                                                :size 1
                                                :status "completed"}))]
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
  (if-let [previous (first (es/get-deployments {:application application
                                                :environment environment
                                                :from 0
                                                :region region
                                                :size 1
                                                :status "completed"}))]
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
      (log/write* id "Resuming deployment")
      (redis/enqueue {:action action
                      :parameters deployment})
      (redis/resume application environment region))))
