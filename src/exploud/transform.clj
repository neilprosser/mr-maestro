(ns exploud.transform
  (:require [exploud.util :as util]))

(defn- completed?
  [tasks]
  (every? (fn [t] (or (= "completed" (:status t))
                     (= "skipped" (:status t)))) tasks))

(defn- status-from
  [old-deployment]
  (let [tasks (:tasks old-deployment)]
    (cond
     (completed? tasks) "completed"
     :else "failed")))

(defn- tasks
  [old-deployment]
  (remove nil? (map (fn [t] (select-keys t [:action :end :id :start :status])) (:tasks old-deployment))))

(defn- logs
  [old-deployment]
  (map #(assoc % :id (util/generate-id)) (remove nil? (flatten (map #(:log %) (:tasks old-deployment))))))

(defn transform
  [old-deployment]
  (let [old-deployment (clojure.walk/keywordize-keys old-deployment)]
    {:application (:application old-deployment)
     :end (:end old-deployment)
     :environment (:environment old-deployment)
     :id (:id old-deployment)
     :message (:message old-deployment)
     :new-state {:auto-scaling-group-name (:newAutoScalingGroupName (:parameters old-deployment))
                 :hash (:hash old-deployment)
                 :image-details {:id (:ami old-deployment)}}
     :phase "deployment"
     :previous-state {:hash (:oldHash (:parameters old-deployment))}
     :region (:region old-deployment)
     :start (:start old-deployment)
     :status (status-from old-deployment)
     :user (:user old-deployment)}))

(comment
  (transform (first (:deployments (cheshire.core/parse-string (:body (clj-http.client/get "http://10.216.138.47:8080/1.x/deployments?from=2081&size=1")) true))))

  (transform (first (:deployments (cheshire.core/parse-string (:body (clj-http.client/get "http://10.216.138.47:8080/1.x/deployments?size=1")) true))))

  (println (logs (first (:deployments (cheshire.core/parse-string (:body (clj-http.client/get "http://10.216.138.47:8080/1.x/deployments?from=2081&size=1")) true)))))

  (println (logs (first (:deployments (cheshire.core/parse-string (:body (clj-http.client/get "http://10.216.138.47:8080/1.x/deployments?size=1")) true)))))

  (println (tasks (first (:deployments (cheshire.core/parse-string (:body (clj-http.client/get "http://10.216.138.47:8080/1.x/deployments?from=2081&size=1")) true)))))

  (println (tasks (first (:deployments (cheshire.core/parse-string (:body (clj-http.client/get "http://10.216.138.47:8080/1.x/deployments?size=1")) true)))))
  )
