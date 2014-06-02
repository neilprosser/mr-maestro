(ns exploud.elasticsearch
  (:require [clj-time.coerce :as c]
            [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.response :as esrsp]
            [environ.core :refer :all]))

(def ^:private conn
  (atom nil))

(def ^:private index-name
  "exploud")

(def ^:private deployment-type
  "deployment")

(def ^:private task-type
  "task")

(def ^:private log-type
  "log")

(defn- update-doc
  [conn index mapping-type id doc & {:as params}]
  (esr/post (esr/record-update-url conn index mapping-type id) :body {:doc doc} :query-params params))

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

(defn environment-filter
  [environment]
  {:term {:environment environment}})

(defn parent-filter
  [type parent-id]
  {:has_parent {:type type
                :query {:term {:_id {:value parent-id}}}}})

(defn init
  []
  (reset! conn (esr/connect (env :elasticsearch-url "http://localhost:9200"))))

(defn upsert-deployment
  [deployment-id document]
  (esd/put @conn index-name deployment-type deployment-id (dissoc document :id) :refresh true))

(defn update-deployment
  [deployment-id partial-document]
  (update-doc @conn index-name deployment-type deployment-id (dissoc partial-document :id) :refresh true))

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
         (map (fn [h] (assoc (:_source h) :id (:_id h)))))))

(defn create-task
  [task-id deployment-id document]
  (esd/put @conn index-name task-type task-id (dissoc document :id) :parent deployment-id :refresh true))

(defn update-task
  [task-id deployment-id partial-document]
  (update-doc @conn index-name task-type task-id (dissoc partial-document :id) :parent deployment-id :refresh true))

(defn write-log
  [log-id deployment-id document]
  (esd/put @conn index-name log-type log-id (dissoc document :id) :parent deployment-id :refresh true))

(defn nil-if-no-deployment
  [deployment-id results]
  (when (or (seq results)
            (deployment deployment-id))
    results))

(defn deployment-tasks
  [deployment-id]
  (nil-if-no-deployment deployment-id (->> (esd/search @conn index-name task-type :query (q/match-all) :filter (parent-filter deployment-type deployment-id) :sort {:sequence "asc"} :size 10000)
                                           esrsp/hits-from
                                           (map (fn [h] (assoc (:_source h) :id (:_id h))))
                                           (map #(dissoc % :sequence)))))

(defn deployment-logs
  [deployment-id since]
  (nil-if-no-deployment deployment-id (let [filters (add-since-date-filter [(parent-filter deployment-type deployment-id)] :date since)]
                                        (->> (esd/search @conn index-name log-type :query (q/match-all) :filter {:and {:filters filters}} :sort {:date "asc"} :size 10000)
                                             esrsp/hits-from
                                             (map (fn [h] (assoc (:_source h) :id (:_id h))))))))

(defn deployments-by-user
  []
  (let [result (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter (completed-status-filter)) :size 0 :facets {:user (user-facet)})
        facets (get-in result [:facets :user :terms])]
    (map (fn [f] {:user (:term f) :count (:count f)}) facets)))

(defn deployments-by-application
  []
  (let [result (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter (completed-status-filter)) :size 0 :facets {:application (application-facet)})
        facets (get-in result [:facets :application :terms])]
    (map (fn [f] {:application (:term f) :count (:count f)}) facets)))

(defn deployments-by-month
  []
  (let [result (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter (completed-status-filter)) :size 0 :facets {:date (start-date-facet "month")})
        facets (get-in result [:facets :date :entries])]
    (map (fn [f] {:date (str (c/from-long (:time f))) :count (:count f)}) facets)))

(defn deployments-by-day
  []
  (let [result (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter (completed-status-filter)) :size 0 :facets {:date (start-date-facet "day")})
        facets (get-in result [:facets :date :entries])]
    (map (fn [f] {:date (str (c/from-long (:time f))) :count (:count f)}) facets)))

(defn deployments-in-environment-by-month
  [environment]
  (let [result (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter {:and {:filters [(completed-status-filter) (environment-filter environment)]}}) :size 0 :facets {:date (start-date-facet "month")})
        facets (get-in result [:facets :date :entries])]
    (map (fn [f] {:date (str (c/from-long (:time f))) :count (:count f)}) facets)))

(defn deployments-in-environment-by-day
  [environment]
  (let [result (esd/search @conn index-name deployment-type :query (q/filtered :query (q/match-all) :filter {:and {:filters [(completed-status-filter) (environment-filter environment)]}}) :size 0 :facets {:date (start-date-facet "day")})
        facets (get-in result [:facets :date :entries])]
    (map (fn [f] {:date (str (c/from-long (:time f))) :count (:count f)}) facets)))
