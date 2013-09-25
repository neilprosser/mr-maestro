(ns exploud.store
  (:require [clj-time.core :as time]
            [clj-time.format :as fmt]
            [monger.collection :as mc])
  (:import org.bson.types.ObjectId))

(def formatter (fmt/formatters :date-time))

(defn get-task [id]
  (mc/find-map-by-id "tasks" (ObjectId. id)))

(defn store-task [{:keys [region run-id workflow-id] :as task}]
  (mc/update "tasks" {:region region :run-id run-id :workflow-id workflow-id} task) :upsert true)

(defn get-configuration [id]
  (mc/find-map-by-id "deployments" (ObjectId. id)))

(defn store-configuration
  "Inserts a new deployment configuration into Mongo and returns the ID of that deployment."
  [application-name {:keys [ami environment hash user]}]
  (let [inserted (mc/insert-and-return "deployments"
                                       {:ami ami
                                        :application application-name
                                        :date (fmt/unparse formatter (time/now))
                                        :environment environment
                                        :hash hash
                                        :user user})]
    {:_id inserted}))

;(store-configuration "skeleton" {:ami "ami-223addf1" :hash "whatever" :user "nprosser"})
;(get-configuration "524033854e080ca4f533f095")
;(get-task "522f06741ea55d2d3aa437e6")
