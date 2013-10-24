(ns exploud.store
  "## Storing all the things

   Currently an integration point with MongoDB allowing the storage of
   deployments and tasks."
  (:require [clj-time
             [core :as time]
             [format :as fmt]]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [dire.core :refer [with-pre-hook! with-post-hook!]]
            [monger
             [collection :as mc]
             [joda-time]
             [operators :refer :all]]))

(defn swap-mongo-id
  "Swaps out an `_id` field for `id`."
  [object]
  (set/rename-keys object {:_id :id}))

(defn swap-id
  "Swaps out an `id` field for `_id`."
  [object]
  (set/rename-keys object {:id :_id}))

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
