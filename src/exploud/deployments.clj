(ns exploud.deployments
  (:require [clj-time.core :as time]
            [exploud
             [elasticsearch :as es]
             [redis :as redis]
             [tasks :as tasks]]))

(defn locked?
  []
  (redis/locked?))

(defn lock
  []
  (redis/lock))

(defn unlock
  []
  (redis/unlock))

(defn in-progress?
  [{:keys [application environment region]}]
  (redis/in-progress? application environment region))

(defn in-progress
  []
  (redis/in-progress))

(defn begin
  [{:keys [application environment id region] :as parameters}]
  (if (redis/begin-deployment parameters)
    (do
      (tasks/enqueue {:action :exploud.messages.data/start-deployment-preparation
                      :parameters (assoc parameters :start (time/now))})
      parameters)
    (throw (ex-info "Deployment already in progress" {:type ::deployment-in-progress
                                                      :application application
                                                      :environment environment
                                                      :region region}))))

(defn end
  [parameters]
  (redis/end-deployment parameters)
  parameters)

(defn undo
  [{:keys [application environment region] :as parameters}]
  (if-let [id (in-progress? parameters)]
    (if-let [deployment (es/deployment id)]
      (if (= "failed" (:status deployment))
        (let [updated-deployment (assoc deployment :undo true)]
          (tasks/enqueue {:action :exploud.messages.data/start-deployment
                          :parameters updated-deployment})
          updated-deployment)
        (throw (ex-info "Deployment has not failed" {:type ::deployment-not-failed
                                                     :id id})))
      (throw (ex-info "Deployment could not be found" {:type ::deployment-not-found
                                                       :id id})))
    (throw (ex-info "Deployment is not in progress" {:type ::deployment-not-in-progress
                                                     :application application
                                                     :environment environment
                                                     :region region}))))

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
            :user user})
    (throw (ex-info "No previous completed deployment could be found" {:type ::previous-completed-deployment-not-found
                                                                       :application application
                                                                       :environment environment}))))
