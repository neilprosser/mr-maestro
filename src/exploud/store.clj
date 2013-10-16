(ns exploud.store
  (:require [clj-time.core :as time]
            [clj-time.format :as fmt]
            [clojure.tools.logging :as log]
            [monger.collection :as mc])
  (:use monger.operators)
  (:import org.bson.types.ObjectId))

(def formatter (fmt/formatters :date-time))

(defn get-task [id]
  (if-let [task (mc/find-map-by-id "tasks" (ObjectId. id))]
    (merge task {:_id (str (:_id task))})))

(defn store-task [{:keys [region url] :as task}]
  (let [find-criteria {:region region :url url}]
    (log/info "Storing task" task)
    (mc/upsert "tasks" find-criteria task)
    (log/info "Done storing task")
    (str (:_id (mc/find-one-as-map "tasks" find-criteria)))))

(defn get-configuration [id]
  (if-let [configuration (mc/find-map-by-id "deployments" (ObjectId. id))]
    (merge configuration {:_id (str (:_id configuration))})))

(defn store-configuration
  "Inserts a new deployment configuration into Mongo and returns the ID of that deployment."
  [application-name {:keys [ami environment hash region user]}]
  (let [inserted (mc/insert-and-return "deployments"
                                       {:ami ami
                                        :application application-name
                                        :date (fmt/unparse formatter (time/now))
                                        :environment environment
                                        :hash hash
                                        :region region
                                        :user user})]
    (str (:_id inserted))))

(defn incomplete-tasks
  "Finds any tasks which are not finished"
  []
  (mc/find-maps "tasks" {$nor [{:status "completed"} {:status "failed"} {:status "terminated"}]}))

;(store-configuration "skeleton" {:ami "ami-223addf1" :environment "dev" :hash "whatever" :region "eu-west-1" :user "nprosser"})
;(get-configuration "524331b94e08331c0378ccda")
;(get-task "522f06741ea55d2d3aa437e6")
;(get-configuration "524324234e08331c0378ccd3")
;(store-task {:region "eu-west-1" :run-id "runid" :workflow-id "workflowid" :something "hello"})
;(incomplete-tasks)
