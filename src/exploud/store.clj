(ns exploud.store
  "## Storing all the things

   Currently an integration point with MongoDB allowing the storage of
   deployments and tasks."
  (:refer-clojure :exclude [sort find])
  (:require [clj-time
             [core :as time]
             [format :as fmt]]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [dire.core :refer [with-pre-hook! with-post-hook!]]
            [monger
             [collection :as mc]
             [joda-time]
             [operators :refer :all]
             [query :refer :all]]))

(defn deployments-collection
  "Define this as a function because then we can override it for testing."
  []
  "deployments")

(defn swap-mongo-id
  "Swaps out an `_id` field for `id`."
  [object]
  (set/rename-keys object {:_id :id}))

(defn swap-id
  "Swaps out an `id` field for `_id`."
  [object]
  (set/rename-keys object {:id :_id}))

(defn add-application-to-query
  "If `application` has been provided, merge it into `query`. Returns the
   query."
  [query application]
  (if application
    (merge query {:application application})
    query))

(defn add-dates-to-query
  "Creates a date-range query for `field` from `start` and `end` and merge into
   `query` if necessary. Returns the query."
  [query field start end]
  (cond (and start end)
        (merge query {field {$gte start $lt end}})
        start
        (merge query {field {$gte start}})
        end
        (merge query {field {$lt end}})
        :else
        query))

(defn get-deployments
  "Retrieves deployments."
  [{:keys [application start-from start-to size from]}]
  (with-collection (deployments-collection)
    (find (-> {}
              (add-application-to-query application)
              (add-dates-to-query :start start-from start-to)))
    (limit (or size 10))
    (skip (or from 0))
    (sort (array-map :start 1))))

(defn get-deployment
  "Retrieves a deployment by its ID."
  [deployment-id]
  (if-let [deployment (mc/find-map-by-id "deployments" deployment-id)]
    (swap-mongo-id deployment)))

;; A pre-hook attached to `get-deployment` for logging.
(with-pre-hook! #'get-deployment
  (fn [id]
    (log/debug "Getting deployment with ID" id)))

(defn store-deployment
  "Stores a deployment. If it doesn't exist, we create it. If it's already
   there, we'll overwrite it with __WHATEVER__ you provide. We __DO NOT__ assign
   an ID to the deployment for you. You're going to have to do that yourself.
   Don't be lazy..."
  [deployment]
  (let [{:keys [_id] :as amended-deployment} (swap-id deployment)]
    (mc/upsert "deployments" {:_id _id} amended-deployment)
    nil))

;; A pre-hook attached to `store-deployment` which logs what we're about to
;; store.
(with-pre-hook! #'store-deployment
  (fn [d]
    (log/debug "Storing deployment" d)))

(defn add-to-deployment-parameters
  "Gets a deployment with `deployment-id` and will merge the given `parameters`
   into the existing `:parameters` from the deployment. Then save it back."
  [deployment-id parameters]
  (let [deployment (get-deployment deployment-id)
        updated-deployment
        (update-in deployment [:parameters] merge parameters)]
    (store-deployment updated-deployment)
    nil))

;; A pre-hook attached to `update-task-in-deployment` to log what we're storing.
(with-pre-hook! #'add-to-deployment-parameters
  (fn [id p]
    (log/debug "Adding parameter" p "to deployment with ID" id)))

(defn update-task-in-deployment
  "Updates a task in the given deployment (where tasks match with identical
   `:id` values). Returns a new deployment."
  [{:keys [tasks] :as deployment} {:keys [id] :as task}]
  (let [amended-tasks (map (fn [t] (if (= (:id t) id)
                                    task
                                    t)) tasks)]
    (assoc-in deployment [:tasks] amended-tasks)))

;; A pre-hook attached to `update-task-in-deployment` to log what we're storing.
(with-pre-hook! #'update-task-in-deployment
  (fn [d t]
    (log/debug "Updating task" t "in deployment" d)))

(defn store-task
  "Stores a task. This function is pretty naïve in that it will find a task
   within the deployment (found by `deployment-id`) with the same `:id` as the
   one given. It then replaces this task and saves the amended deployment."
  [deployment-id task]
  (when-let [deployment (get-deployment deployment-id)]
    (store-deployment (update-task-in-deployment deployment task))
    nil))

;; A pre-hook attached to `store-task` to log what we're storing.
(with-pre-hook! #'store-task
  (fn [id t]
    (log/debug "Storing task" t "against deployment with ID" id)))

(defn deployments-with-incomplete-tasks
  "Gives a list of any deployments with tasks which are not finished. We use
   this so they can be restarted if Exploud is stopped for any reason."
  []
  (mc/find-maps "deployments"
                {:tasks {$elemMatch {$nor [{:status "completed"}
                                           {:status "failed"}
                                           {:status "terminated"}
                                           {:status "pending"}]}}}
                ["tasks.$"]))
