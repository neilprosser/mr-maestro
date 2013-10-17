(ns exploud.asgard_new
  "## Integration with Asgard

   We integrate with a few parts of [Asgard](https://github.com/Netflix/asgard). It's all good, at least until we have to switch to Azure.

   Asgard introduces a few concepts on top of AWS:

   - __Application__ - An application is a simple construct which pulls together a name, owner and an email address to send notifications about it.
   - __Cluster__ - A cluster is the collection of Auto Scaling Groups which form an application.
   - __Stack__ - A stack is a way of extending the naming conventions which Asgard uses to allow many groupings. We use it in our case as a synonym for environment."
  (:require [cheshire.core :as json]
            [clj-time.format :as fmt]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [dire.core :refer [with-handler! with-precondition! with-post-hook!]]
            [environ.core :refer [env]]
            [exploud
             [http :as http]
             [store :as store]
             [tyranitar :as tyr]]
            [overtone.at-at :as at-at]))

;; # General def-age

;; The date formatter we'll use to write out our nicely parsed dates.
(def date-formatter (fmt/formatters :date-time-no-ms))

;; The formatter used by Asgard in log messages.
(def asgard-log-date-formatter (fmt/formatter "YYYY-MM-dd_HH:mm:ss"))

;; The formatter used by Asgard in its `updateTime` field. Why would you want to use the same format in two places?
(def asgard-update-time-formatter (fmt/formatter "YYYY-MM-dd HH:mm:ss z"))

;; The number of seconds we'll keep tracking a task for, before giving up.
(def task-track-count (* 1 60 60))

;; # Concerning Asgard URL generation

;; The URL where Asgard is deployed.
(def asgard-url
  (env :service-asgard-url))

(defn- auto-scaling-group-url
  "Gives us a region-based URL we can use to get information about an Auto Scaling Group."
  [region asg-name]
  (str asgard-url "/" region "/autoScaling/show/" asg-name ".json"))

(defn- auto-scaling-save-url
  "Gives us a region-based URL we can use to save Auto Scaling Groups."
  [region]
  (str asgard-url "/" region "/autoScaling/save"))

(defn- cluster-create-next-group-url
  "Gives us a region-based URL we can use to create the next Auto Scaling Group."
  [region]
  (str asgard-url "/" region "/cluster/createNextGroup"))

(defn- cluster-index-url
  "Gives us a region-based URL we can use to make changes to Auto Scaling Groups."
  [region]
  (str asgard-url "/" region "/cluster/index"))

(defn- tasks-url
  "Gives us a region-based URL we can use to get tasks."
  [region]
  (str asgard-url "/" region "/task/list.json"))

(defn- task-by-id-url
  "Gives us a region-based URL we can use to get a single task by its ID."
  [region task-id]
  (str asgard-url "/" region "/task/show/" task-id ".json"))

;; # Task transformations

(defn- split-log-message
  "Splits a log message from its Asgard form (where each line is something like `2013-10-11_18:25:23 Completed in 1s.`) to a map with separate `:date` and `:message` fields."
  [log]
  (let [[date message] (clojure.string/split log #" " 2)]
    {:date (fmt/unparse date-formatter (fmt/parse asgard-log-date-formatter date)) :message message}))

(defn- correct-date-time
  "Parses a task date/time from its Asgard form (like `2013-10-11 14:20:42 UTC`) to an ISO8601 one. Unfortunately has to do a crappy string-replace of `UTC` for `GMT`, ugh..."
  [date]
  (fmt/unparse date-formatter (fmt/parse asgard-update-time-formatter (str/replace date "UTC" "GMT"))))

(defn- munge-task
  "Converts an Asgard task to a desired form by using `split-log-message` on each line of the `:log` in the task and replacing it."
  [{:keys [log updateTime] :as task}]
  (cond log
        (assoc-in task [:log] (map split-log-message log))
        updateTime
        (assoc-in task [:updateTime] (correct-date-time updateTime))))

;; # Concerning grabbing objects from Asgard

(defn auto-scaling-group
  "Retrieves information about an Auto Scaling Group from Asgard."
  [region asg-name]
  (let [{:keys [status body]} (http/simple-get (auto-scaling-group-url region asg-name))]
    (when (= status 200)
      (json/parse-string body true))))

(defn task-by-url
  "Retrieves a task by its URL."
  [task-url]
  (let [{:keys [body status]} (http/simple-get task-url)]
    (when (= status 200)
      (munge-task (json/parse-string body true)))))

(defn tasks
  "Retrieves all tasks that Asgard knows about. It will combine `:runningTaskList` with `:completedTaskList` (with the running tasks first in the list)."
  [region]
  (let [{:keys [body status]} (http/simple-get (tasks-url region))]
    (when (= status 200)
      (let [both (json/parse-string)]
        (concat (:runningTaskList both) (:completedTaskList both))))))

;; # Concerning tracking tasks

;; A pool which we use to refresh the tasks from Asgard.
(def task-pool (at-at/mk-pool))

;; The states at which a task is deemed finished.
(def finished-states #{"completed" "failed" "terminated"})

(defn finished?
  "Indicates whether the task is completed."
  [task]
  (contains? finished-states (:status task)))

(declare track-task)

(defn track-until-completed
  "After a 1s delay, tracks the task by saving its content to the task store until it is completed (as indicated by `is-finished?`) or `count` reaches 0."
  [ticket-id {:keys [url] :as task} count]
  (at-at/after 1000 #(track-task ticket-id task count) task-pool :desc url))

(defn track-task
  "Grabs the task by its URL and updates its details in the store. If it's not completed and we've not exhausted the retry count it'll reschedule itself."
  [ticket-id {:keys [url] :as task} count]
  (when-let [asgard-task (task-by-url url)]
    (store/store-task (merge task asgard-task))
    (when (and (not (finished? asgard-task))
               (pos? count))
      (track-until-completed ticket-id task (dec count)))))

;; Handler for recovering from failure while tracking a task. In the event of an exception marked with a `:class` of `:http` or `:store` we'll reschedule.
(with-handler! #'track-task
  clojure.lang.ExceptionInfo
  (fn [e ticket-id task count]
    (let [data (.getData e)]
      (if (or (= (:class data) :http)
              (= (:class data) :store))
        (track-until-completed ticket-id task (dec count))
        (throw e)))))

;; # Concerning deleting ASGs

(defn delete-asg
  "Begins a delete operation on the specified Auto Scaling Group in the region given. Will start tracking the resulting task URL until completed. You can assume that a non-explosive call has been successful and the task is being tracked."
  [region asg-name ticket-id task]
  (let [params {"_action_delete" ""
                "name" asg-name
                "ticket" ticket-id}
        {:keys [status headers] :as response} (http/simple-post
                                               (cluster-index-url region)
                                               {:form-params params})]
    (if (= status 302)
      (track-until-completed ticket-id (merge task {:url (get headers "location")}) task-track-count)
      (throw (ex-info "Unexpected status while deleting ASG"
                      {:type ::unexpected-response
                       :response response})))))

;; Precondition attached to `delete-asg` asserting that the ASG we're attempting to delete exists.
(with-precondition! #'delete-asg
  :asg-exists
  (fn [r a _ _]
    (auto-scaling-group r a)))

;; Handler for a missing ASG attached to `delete-asg`.
(with-handler! #'delete-asg
  {:precondition :asg-exists}
  (fn [e & args] (throw (ex-info "Auto Scaling Group does not exist."
                                {:type ::missing-asg
                                 :args args}))))

;; # Concerning resizing ASGs

(defn resize-asg
  "Begins a resize operation on the specified Auto Scaling Group in the region given. Will start tracking the resulting task URL until completed. You can assume that a non-explosive call has been successful and the task is being tracked."
  [region asg-name ticket-id task new-size]
  (let [params {"_action_resize" ""
                "minAndMaxSize" new-size
                "name" asg-name
                "ticket" ticket-id}
        {:keys [status headers] :as response} (http/simple-post
                                               (cluster-index-url region)
                                               {:form-params params})]
    (if (= status 302)
      (track-until-completed ticket-id (merge task {:url (get headers "location")}) task-track-count)
      (throw (ex-info "Unexpected status while resizing ASG"
                      {:type ::unexpected-response
                       :response response})))))

;; Precondition attached to `resize-asg` asserting that the ASG we're attempting to resize exists.
(with-precondition! #'resize-asg
  :asg-exists
  (fn [r a _ _ _]
    (auto-scaling-group r a)))

;; Handler for a missing ASG attached to `resize-asg`.
(with-handler! #'resize-asg
  {:precondition :asg-exists}
  (fn [e & args] (throw (ex-info "Auto Scaling Group does not exist."
                                {:type ::missing-asg
                                 :args args}))))

;; ## Concerning enabling traffic for ASGs

(defn enable-asg
  "Begins an enable traffic operation on the specified Auto Scaling Group in the region given. Will start tracking the resulting task URL until completed. You can assume that a non-explosive call has been successful and the task is being tracked."
  [region asg-name ticket-id task]
  (let [params {"_action_activate" ""
                "name" asg-name
                "ticket" ticket-id}
        {:keys [status headers] :as response} (http/simple-post
                                               (cluster-index-url region)
                                               {:form-params params})]
    (if (= status 302)
      (track-until-completed ticket-id (merge task {:url (get headers "location")}) task-track-count)
      (throw (ex-info "Unexpected status while enabling ASG"
                      {:type ::unexpected-response
                       :response response})))))

;; Precondition attached to `enable-asg` asserting that the ASG we're attempting to enable exists.
(with-precondition! #'enable-asg
  :asg-exists
  (fn [r a _ _]
    (auto-scaling-group r a)))

;; Handler for a missing ASG attached to `enable-asg`.
(with-handler! #'enable-asg
  {:precondition :asg-exists}
  (fn [e & args] (throw (ex-info "Auto Scaling Group does not exist."
                                {:type ::missing-asg
                                 :args args}))))

;; ## Concerning disabling traffic for ASGs

(defn disable-asg
  "Begins an disable traffic operation on the specified Auto Scaling Group in the region given. Will start tracking the resulting task URL until completed. You can assume that a non-explosive call has been successful and the task is being tracked."
  [region asg-name ticket-id task]
  (let [params {"_action_deactivate" ""
                "name" asg-name
                "ticket" ticket-id}
        {:keys [status headers] :as response} (http/simple-post
                                               (cluster-index-url region)
                                               {:form-params params})]
    (if (= status 302)
      (track-until-completed ticket-id (merge task {:url (get headers "location")}) task-track-count)
      (throw (ex-info "Unexpected status while disabling ASG"
                      {:type ::unexpected-response
                       :response response})))))

;; Precondition attached to `disable-asg` asserting that the ASG we're attempting to disable exists.
(with-precondition! #'disable-asg
  :asg-exists
  (fn [r a _ _]
    (auto-scaling-group r a)))

;; Handler for a missing ASG attached to `disable-asg`.
(with-handler! #'disable-asg
  {:precondition :asg-exists}
  (fn [e & args] (throw (ex-info "Auto Scaling Group does not exist."
                                {:type ::missing-asg
                                 :args args}))))

;; # Concerning the creation of the first ASG for an application

(defn extract-new-asg-name
  "Extracts the new ASG name from what would be the `Location` header of the response when asking Asgard to create a new ASG. Takes something like `http://asgard/{region}/autoScaling/show/{asg-name}` and gives back `{asg-name}`."
  [url]
  (second (re-find #"/autoScaling/show/(.+)$" url)))

(defn create-new-asg
  "Begins a create new Auto Scaling Group operation for the specified application and environment in the region given. It __WILL__ start traffic to the newly-created ASG. Will start tracking the resulting task URL until completed. You can assume that a non-explosive call has been successful and the task is being tracked."
  [region application-name environment image-id commit-hash ticket-id task]
  (let [params {"_action_save" ""
                "appName" application-name
                "imageId" image-id
                "keyName" "nprosser-key"
                "stack" environment
                "ticket" ticket-id}
        user-params (tyr/deployment-params environment application-name commit-hash)
        asgard-params (merge user-params params)
        {:keys [status headers] :as response} (http/simple-post
                                               (auto-scaling-save-url region)
                                               {:form-params asgard-params})]
    (if (= status 302)
      (let [new-asg-name (extract-new-asg-name (get headers "location"))
            tasks (tasks region)]
        (when-let [found-task (first (filter (fn [t] (= (:name t) (str "Create Auto Scaling Group '" new-asg-name "'"))) tasks))]
          (let [task-id (:id found-task)
                url (task-by-id-url region task-id)]
            (track-until-completed ticket-id (merge task {:url url}) task-track-count)))
        new-asg-name)
      (throw (ex-info "Unexpected status while creating new ASG"
                      {:type ::unexpected-response
                       :response response})))))

;; # Concerning the creation of the next ASG for an application-name

(defn create-next-asg
  "Begins a create next Auto Scaling Group operation for the specified application and environment in the region given. It __WILL NOT__ start traffic to the newly-created ASG. Will start tracking the resulting task URL until completed. You can assume that a non-explosive call has been successful and the task is being tracked."
  [region application-name environment image-id commit-hash ticket-id task]
  (let [params {"_action_createNextGroup" ""
                "imageId" image-id
                "keyName" "nprosser-key"
                "name" (str application-name "-" environment)
                "ticket" ticket-id
                "trafficAllowed" "off"}
        user-params (tyr/deployment-params environment application-name commit-hash)
        asgard-params (merge user-params params)
        {:keys [status headers] :as response} (http/simple-post
                                               (cluster-create-next-group-url region)
                                               {:form-params asgard-params})]
    (if (= status 302)
      nil
      (throw (ex-info "Unexpected status while creating next ASG"
                      {:type ::unexpected-response
                       :response response})))))
