(ns exploud.asgard
  (:require [cheshire.core :as json]
            [clj-time.format :as fmt]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [exploud.http :as http]
            [exploud.store :as store]
            [overtone.at-at :as at-at]))

(def tp (at-at/mk-pool))

(def asgard-date-formatter (fmt/formatter "YYYY-MM-dd_HH:mm:ss"))
(def date-formatter (fmt/formatters :date-time))

(def asgard-url
  (env :service-asgard-url))

(defn- application-url [application-name]
  (str asgard-url "/application/show/" application-name ".json"))

(defn- cluster-url [region cluster-name]
  (str asgard-url "/" region "/cluster/show/" cluster-name ".json"))

(defn- launch-configuration-url [region launch-configuration-name]
  (str asgard-url "/" region "/launchConfiguration/show/" launch-configuration-name ".json"))

(defn- task-url []
  (str asgard-url "/task/show.json"))

(defn- region-deploy-url [region]
  (str asgard-url "/" region "/cluster/deploy"))

(defn- image-list-url [region application-name]
  (str asgard-url "/" region "/image/list/" application-name ".json"))

(defn application
  "Retrieves information about an application from Asgard"
  [application-name]
  (let [response (http/simple-get (application-url application-name))
        {:keys [status]} response]
    (when (= status 200)
      (json/parse-string (:body response) true))))

(defn cluster
  "Retrieves information about a cluster from Asgard"
  [region cluster-name]
  (let [{:keys [body status]} (http/simple-get (cluster-url region cluster-name))]
    (when (= status 200)
      (json/parse-string body true))))

(defn image-list
  "Retrives the list of images associated with an application."
  [region application-name]
  (let [{:keys [body status]} (http/simple-get (image-list-url region application-name))]
    (when (= status 200)
      (json/parse-string body true))))

(defn launch-configuration
  "Retrieves information about a launch configuration from Asgard"
  [region launch-configuration-name]
  (let [{:keys [body status]} (http/simple-get (launch-configuration-url region launch-configuration-name))]
    (when (= status 200)
      (json/parse-string body true))))

(defn last-launch-configuration-name
  "Gets the name of the last launch configuration for an application"
  [region application-name]
  (:launchConfigurationName (last (cluster region application-name))))

(defn last-security-groups
  "Gets the names of the last security groups an application was launched with"
  [region application-name]
  (:securityGroups (:lc (launch-configuration region (last-launch-configuration-name region application-name)))))

(defn- split-log-message [log]
  (let [[date message] (clojure.string/split log #" " 2)]
    {:date (fmt/unparse date-formatter (fmt/parse asgard-date-formatter date)) :message message}))

(defn- munge-task [task]
  "Converts an Asgard task to a desired form"
  (update-in task [:log] (fn [log] (map #(split-log-message %) log))))

(defn task [region run-id workflow-id]
  "Retrives information about a task from Asgard"
  (let [{:keys [body status]} (http/simple-get (task-url) {:query-params {:runId run-id :workflowId workflow-id}})]
    (when (= status 200)
      (munge-task (json/parse-string body true)))))

(defn- task-tracking-desc [region run-id workflow-id]
  (str "task-track-" region "-" run-id "-" workflow-id))

(defn- job-by-desc [desc]
  (filter #(= (:desc %) desc) (at-at/scheduled-jobs tp)))

(defn- finished? [task]
  (or (= (:status task) "completed")
      (= (:status task) "failed")
      (= (:status task) "terminated")))

(defn- stop-job-by-desc [desc]
  (map at-at/stop (job-by-desc desc)))

(declare track-task)

(defn- schedule-track-task [region run-id workflow-id count]
  (log/info "Scheduling task tracking for" region run-id workflow-id)
  (at-at/after 1000 #(track-task region run-id workflow-id count) tp :desc (task-tracking-desc region run-id workflow-id)))

(defn- track-task [region run-id workflow-id count]
  (log/info "Generating task description")
  (let [desc (task-tracking-desc region run-id workflow-id)]
    (log/info "Getting task")
    (if-let [task (task region run-id workflow-id)]
      (do
        (log/info "Storing information for" region run-id workflow-id)
        (log/info "Task is" task)
        (store/store-task (merge task {:region region :runId run-id :workflowId workflow-id}))
        (log/info "Should we be done already? Finished" (finished? task) "Count" count)
        (if (and (not (finished? task))
                 (pos? count))
          (do
            (log/info "Rescheduling task tracking for" region run-id workflow-id)
            (schedule-track-task region run-id workflow-id (dec count)))
          (log/info "Task" region run-id workflow-id "is stopping being tracked")))
      (log/info "No task information for" region run-id workflow-id))))

(defn- task-info-from-url [url]
  (into {} (map (fn [[k v]] {(keyword k) (ring.util.codec/url-decode v)}) (map #(clojure.string/split % #"=") (clojure.string/split (nth (clojure.string/split url #"\?" 2) 1) #"&")))))

(defn deploy
  "Kicks off an automated red/black deployment. If successful, the task
  information will be tracked until the task has completed. This function
  returns a map containing the task information which can be used to
  retrieve the task and track the deployment."
  [region application-name params]
  (log/info "Application" application-name "is being deployed in" region "with params" params)
  (when-let [application (application application-name)]
    (let [{:keys [headers status]} (http/simple-post (region-deploy-url region) {:form-params params :follow-redirects false})]
      (when (= 302 status)
        (log/info "Headers are" headers)
        (let [{:strs [location]} headers
              task-info (task-info-from-url location)
              {:keys [runId workflowId]} task-info
              task-id (store/store-task {:region region :runId runId :workflowId workflowId})]
          (schedule-track-task region runId workflowId (* 1 60 60))
          {:taskId task-id})))))

;(task "eu-west-1" "120DqQWqEBYsOK5Vaj6sK8b4YErobn8sF61FcN7OA63t0=" "b7287ace-cfd3-41b6-9618-189534d9f207")
;(last-launch-configuration-name "eu-west-1" "skeleton")
;(last-security-groups "eu-west-1" "skeleton")
;(task-info-from-url "http://asgard.brislabs.com:8080/task/show?runId=12lyXMu%2FrpdVtZ%2FpgbUQ7hpuAEzizQ9QKa0fnpj7G8fwE%3D&workflowId=a99a149d-be24-47c8-93c0-d10e00334dee")
