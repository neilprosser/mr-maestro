(ns exploud.store
  "## Storing all the things

   Currently an integration point with MongoDB allowing the storage of deployments and tasks."
  (:require [clj-time
             [core :as time]
             [format :as fmt]]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [monger
             [collection :as mc]
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

(defn store-deployment
  "Stores a deployment. If it doesn't exist, we create it. If it's already there, we'll overwrite it with __WHATEVER__ you provide. We __DO NOT__ assign an ID to the deployment for you. You're going to have to do that yourself. Don't be lazy..."
  [deployment]
  (let [{:keys [_id] :as amended-deployment} (swap-id deployment)]
    (mc/upsert "deployments" {:_id _id} amended-deployment)
    nil))

(defn update-task-in-deployment
  "Updates a task in the given deployment (where tasks match with identical `:id` values). Returns a new deployment."
  [{:keys [tasks] :as deployment} {:keys [id] :as task}]
  (let [amended-tasks (map (fn [t] (if (= (:id t) id)
                                    task
                                    t)) tasks)]
    (assoc-in deployment [:tasks] amended-tasks)))

(defn store-task
  "Stores a task. This function is pretty naïve in that it will find a task within the deployment (found by `deployment-id`) with the same `:id` as the one given. It then replaces this task and saves the amended deployment."
  [deployment-id task]
  (when-let [deployment (get-deployment deployment-id)]
    (store-deployment (update-task-in-deployment deployment task))
    nil))

(defn deployments-with-incomplete-tasks
  "Gives a list of any deployments with tasks which are not finished. We use this so they can be restarted if Exploud is stopped for any reason."
  []
  (mc/find-maps "deployments"
                {:tasks {$elemMatch {$nor [{:status "completed"} {:status "failed"} {:status "terminated"}]}}}
                ["tasks.$"]))
