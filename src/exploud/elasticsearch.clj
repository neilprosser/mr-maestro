(ns exploud.elasticsearch
  (:require [clj-time.coerce :as c]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.response :as esrsp]
            [environ.core :refer :all]))

(def ^:private index-name
  "exploud")

(def ^:private deployment-type
  "deployment")

(def ^:private task-type
  "task")

(def ^:private log-type
  "log")

(defn- update-doc
  [index mapping-type id doc & {:as params}]
  (esr/post (esr/record-update-url index mapping-type id) :body {:doc doc} :query-params params))

(defn start-date-facet
  []
  {:date_histogram {:field "start" :interval "month"}})

(defn application-facet
  []
  {:terms {:field "application" :order "term" :size 1000}})

(defn user-facet
  []
  {:terms {:field "user" :order "term" :size 1000}})

(defn completed-status-filter
  []
  {:term {:status "completed"}})

(defn environment-filter
  [environment]
  {:term {:environment environment}})

(defn init
  []
  (esr/connect! (env :elasticsearch-url "http://localhost:9200")))

(defn upsert-deployment
  [deployment-id document]
  (esd/create index-name deployment-type (dissoc document :id) :id deployment-id :refresh true)
  (esi/refresh index-name))

(defn update-deployment
  [deployment-id partial-document]
  (update-doc index-name deployment-type deployment-id (dissoc partial-document :id))
  (esi/refresh index-name))

(defn deployment
  [deployment-id]
  (when-let [document (:_source (esd/get index-name deployment-type deployment-id :routing deployment-id))]
    (assoc document :id deployment-id)))

(defn- add-filter
  [filters field value]
  (if value
    (merge filters {:term {field value}})
    filters))

(defn- create-date-filter
  [field from to]
  (let [from-filter (if from {:gte from})
        to-filter (if to {:lt to})]
    (when (or from-filter
              to-filter)
      {:range {field (merge from-filter to-filter)}})))

(defn- create-since-date-filter
  [field from]
  (let [from-filter (if from {:gt from})]
    (when from-filter
      {:range {field from-filter}})))

(defn- add-date-filter
  [filters field from to]
  (if-let [filter (create-date-filter field from to)]
    (merge filters filter)
    filters))

(defn- add-since-date-filter
  [filters field from]
  (if-let [filter (create-since-date-filter field from)]
    (merge filters filter)
    filters))

(defn get-deployments
  [{:keys [application environment from region size start-from start-to status]}]
  (let [filters (-> []
                    (add-filter :application application)
                    (add-filter :environment environment)
                    (add-filter :region region)
                    (add-filter :status status)
                    (add-date-filter :start start-from start-to))
        response (esd/search index-name deployment-type :filter {:and {:filters filters}} :size (or size 10) :from (or from 0) :sort {:start "desc"})]
    (->> response
         esrsp/hits-from
         (map (fn [h] (assoc (:_source h) :id (:_id h)))))))

(defn create-task
  [task-id deployment-id document]
  (esd/create index-name task-type (dissoc document :id) :id task-id :parent deployment-id :refresh true)
  (esi/refresh index-name))

(defn update-task
  [task-id deployment-id partial-document]
  (update-doc index-name task-type task-id (dissoc partial-document :id) :parent deployment-id)
  (esi/refresh index-name))

(defn write-log
  [log-id deployment-id document]
  (esd/create index-name log-type (dissoc document :id) :id log-id :parent deployment-id :refresh true)
  (esi/refresh index-name))

(defn deployment-tasks
  [deployment-id]
  (->> (esd/search index-name task-type :routing deployment-id :query (q/match-all) :filter {:has_parent {:type deployment-type :query {:term {:_id {:value deployment-id}}}}} :sort {:sequence "asc"} :size 10000)
       esrsp/hits-from
       (map (fn [h] (assoc (:_source h) :id (:_id h))))
       (map #(dissoc % :sequence))))

(defn parent-filter
  [type parent-id]
  {:has_parent {:type type
                :query {:term {:_id {:value parent-id}}}}})

(defn deployment-logs
  [deployment-id since]
  (let [filters (-> [(parent-filter deployment-type deployment-id)]
                    (add-since-date-filter :date since))]
    (->> (esd/search index-name log-type :routing deployment-id :query (q/match-all) :filter {:and {:filters filters}} :sort {:date "asc"} :size 10000)
         esrsp/hits-from
         (map (fn [h] (assoc (:_source h) :id (:_id h)))))))

(defn deployments-by-user
  []
  (let [result (esd/search index-name deployment-type :query (q/filtered :query (q/match-all) :filter (completed-status-filter)) :size 0 :facets {:user (user-facet)})
        facets (get-in result [:facets :user :terms])]
    (map (fn [f] {:user (:term f) :count (:count f)}) facets)))

(defn deployments-by-application
  []
  (let [result (esd/search index-name deployment-type :query (q/filtered :query (q/match-all) :filter (completed-status-filter)) :size 0 :facets {:application (application-facet)})
        facets (get-in result [:facets :application :terms])]
    (map (fn [f] {:application (:term f) :count (:count f)}) facets)))

(defn deployments-by-month
  []
  (let [result (esd/search index-name deployment-type :query (q/filtered :query (q/match-all) :filter (completed-status-filter)) :size 0 :facets {:date (start-date-facet)})
        facets (get-in result [:facets :date :entries])]
    (map (fn [f] {:date (str (c/from-long (:time f))) :count (:count f)}) facets)))

(defn deployments-in-environment-by-month
  [environment]
  (let [result (esd/search index-name deployment-type :query (q/filtered :query (q/match-all) :filter {:and {:filters [(completed-status-filter) (environment-filter environment)]}}) :size 0 :facets {:date (start-date-facet)})
        facets (get-in result [:facets :date :entries])]
    (map (fn [f] {:date (str (c/from-long (:time f))) :count (:count f)}) facets)))
