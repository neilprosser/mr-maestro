(ns maestro.elasticsearch
  (:require [clj-time.coerce :as c]
            [clojure.tools.logging :refer [warn]]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.response :as esrsp]
            [environ.core :refer :all]))

(def ^:private conn
  (atom nil))

(def ^:private index-name
  (env :elasticsearch-index-name "maestro"))

(def ^:private deployment-type
  "deployment")

(def ^:private task-type
  "task")

(def ^:private log-type
  "log")

(defn start-date-facet
  [interval]
  {:date_histogram {:field "start" :interval interval}})

(defn application-facet
  []
  {:terms {:field "application" :order "term" :size 1000}})

(defn user-facet
  []
  {:terms {:field "user" :order "term" :size 1000}})

(defn completed-status-filter
  []
  {:term {:status "completed"}})

(defn failed-status-filter
  []
  {:or [{:term {:status "failed"}} {:and [{:term {:status "completed"}} {:term {:undo true}}]}]})

(defn environment-filter
  [environment]
  {:term {:environment environment}})

(defn user-filter
  [user]
  {:term {:user user}})

(defn parent-filter
  [document-type parent-id]
  {:has_parent {:type document-type
                :query {:term {:_id {:value parent-id}}}}})

(defn upsert-deployment
  [deployment-id document]
  (esd/put @conn index-name deployment-type deployment-id (dissoc document :id) :refresh true))

(defn update-deployment
  [deployment-id partial-document]
  (esd/update-with-partial-doc @conn index-name deployment-type deployment-id (dissoc partial-document :id) :refresh true))

(defn deployment
  [deployment-id]
  (when-let [document (:_source (esd/get @conn index-name deployment-type deployment-id))]
    (assoc document :id deployment-id)))

(defn delete-deployment
  [deployment-id]
  (esd/delete @conn index-name deployment-type deployment-id)
  (esd/delete-by-query-across-all-types @conn index-name (q/filtered :query (q/match-all) :filter (parent-filter deployment-type deployment-id)))
  (esi/refresh @conn index-name))

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

(defn- source-filter
  [full?]
  (if full?
    true
    {:include ["application" "end" "environment" "message" "phase" "region" "start" "status" "user"
               "new-state.hash" "new-state.image-details"
               "previous-state.hash" "previous-state.image-details"]}))

(defn- map-to-source-with-id
  [hits]
  (map (fn [h] (assoc (:_source h) :id (:_id h))) hits))

(defn get-deployments
  [{:keys [application environment full? from region size start-from start-to status]}]
  (let [filters (-> []
                    (add-filter :application application)
                    (add-filter :environment environment)
                    (add-filter :region region)
                    (add-filter :status status)
                    (add-date-filter :start start-from start-to))
        response (esd/search @conn index-name deployment-type :filter {:and {:filters filters}} :size (or size 10) :from (or from 0) :sort {:start "desc"} :_source (source-filter full?))]
    (->> response
         esrsp/hits-from
         map-to-source-with-id)))

(defn create-task
  [task-id deployment-id document]
  (esd/put @conn index-name task-type task-id (dissoc document :id) :parent deployment-id :refresh true))

(defn update-task
  [task-id deployment-id partial-document]
  (esd/update-with-partial-doc @conn index-name task-type task-id (dissoc partial-document :id) :parent deployment-id :refresh true))

(defn write-log
  [log-id deployment-id document]
  (esd/put @conn index-name log-type log-id (dissoc document :id) :parent deployment-id :refresh true))

(defn- nil-if-no-deployment
  [deployment-id results]
  (when (or (seq results)
            (deployment deployment-id))
    results))

(defn deployment-tasks
  [deployment-id]
  (nil-if-no-deployment deployment-id (->> (esd/search @conn index-name task-type :query (q/match-all) :filter (parent-filter deployment-type deployment-id) :sort {:sequence "asc"} :size 10000)
                                           esrsp/hits-from
                                           map-to-source-with-id
                                           (map #(dissoc % :sequence)))))

(defn deployment-logs
  [deployment-id since]
  (nil-if-no-deployment deployment-id (let [filters (add-since-date-filter [(parent-filter deployment-type deployment-id)] :date since)]
                                        (->> (esd/search @conn index-name log-type :query (q/match-all) :filter {:and {:filters filters}} :sort {:date "asc"} :size 10000)
                                             esrsp/hits-from
                                             map-to-source-with-id))))

(defn- map-user-facets
  [result]
  (map (fn [f] {:user (:term f) :count (:count f)}) (get-in result [:facets :user :terms])))

(defn deployments-by-user
  []
  (map-user-facets (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter (completed-status-filter)) :size 0 :facets {:user (user-facet)})))

(defn failed-deployments-by-user
  []
  (map-user-facets (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter (failed-status-filter)) :size 0 :facets {:user (user-facet)})))

(defn- map-application-facets
  [result]
  (map (fn [f] {:application (:term f) :count (:count f)}) (get-in result [:facets :application :terms])))

(defn deployments-by-user-by-application
  [user]
  (map-application-facets (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter {:and {:filters [(completed-status-filter) (user-filter user)]}}) :size 0 :facets {:application (application-facet)})))

(defn deployments-by-application
  []
  (map-application-facets (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter (completed-status-filter)) :size 0 :facets {:application (application-facet)})))

(defn- map-date-facets
  [result]
  (map (fn [f] {:date (str (c/from-long (:time f))) :count (:count f)}) (get-in result [:facets :date :entries])))

(defn deployments-by-year
  []
  (map-date-facets (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter (completed-status-filter)) :size 0 :facets {:date (start-date-facet "year")})))

(defn deployments-by-month
  []
  (map-date-facets (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter (completed-status-filter)) :size 0 :facets {:date (start-date-facet "month")})))

(defn deployments-by-day
  []
  (map-date-facets (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter (completed-status-filter)) :size 0 :facets {:date (start-date-facet "day")})))

(defn deployments-in-environment-by-year
  [environment]
  (map-date-facets (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter {:and {:filters [(completed-status-filter) (environment-filter environment)]}}) :size 0 :facets {:date (start-date-facet "year")})))

(defn deployments-in-environment-by-month
  [environment]
  (map-date-facets (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter {:and {:filters [(completed-status-filter) (environment-filter environment)]}}) :size 0 :facets {:date (start-date-facet "month")})))

(defn deployments-in-environment-by-day
  [environment]
  (map-date-facets (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter {:and {:filters [(completed-status-filter) (environment-filter environment)]}}) :size 0 :facets {:date (start-date-facet "day")})))

(defn healthy?
  []
  (try
    (deployment "healthcheck")
    true
    (catch Exception e
      (warn e "Failed to check Elasticsearch health")
      false)))

(defn init
  []
  (reset! conn (esr/connect (env :elasticsearch-url "http://localhost:9200"))))
