(ns exploud.asgard
  (:require [cheshire.core :as json]
            [environ.core :refer [env]]
            [exploud.http :as http]
            [exploud.store :as store]
            [overtone.at-at :as at-at]))

(def tp (at-at/mk-pool))

(def asgard-url
  (env :service-asgard-url))

(defn- application-url [application-name]
  (str asgard-url "/application/show/" application-name ".json"))

(defn- cluster-url [region cluster-name]
  (str asgard-url "/" region "/cluster/show/" cluster-name ".json"))

(defn- launch-configuration-url [region launch-configuration-name]
  (str asgard-url "/" region "/launchConfiguration/show/" launch-configuration-name ".json"))

(defn- task-url [region run-id workflow-id]
  (str asgard-url "/" region "/task/show.json" {:query-params {"runId" run-id "workflowId" workflow-id}}))

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

(defn task [region run-id workflow-id]
  "Retrives information about a task from Asgard"
  (let [{:keys [body status]} (http/simple-get (task-url region run-id workflow-id))]
    (when (= status 200)
      (json/parse-string body true))))

(defn- task-tracking-desc [region run-id workflow-id]
  (str "task-track-" region "-" run-id "-" workflow-id))

(defn- job-by-desc [desc]
  (filter #(= (:desc %) desc) (at-at/scheduled-jobs tp)))

(defn- finished? [task]
  (or (= (:status task) "completed")
      (= (:status task) "terminated")))

(defn- stop-job-by-desc [desc]
  (map at-at/stop (job-by-desc desc)))

(declare track-task)

(defn- schedule-track-task [region run-id workflow-id count]
  (at-at/after 1000 #(track-task region run-id workflow-id count) tp :desc (task-tracking-desc region run-id workflow-id)))

(defn- track-task [region run-id workflow-id count]
  (let [desc (task-tracking-desc region run-id workflow-id)]
    (when-let [task (task region run-id workflow-id)]
      (store/store-task (merge-with + :region region :run-id run-id :workflow-id workflow-id task))
      (when (and (not (finished? task))
                 (pos? count))
        (schedule-track-task region run-id workflow-id (dec count))))))

(defn- task-info-from-url [url]
  (into {} (map (fn [k v] {(keyword k) v}) (map #(clojure.string/split % #"=") (clojure.string/split (nth (clojure.string/split url #"\?" 2) 1) #"&")))))

(defn deploy
  "Kicks off an automated red/black deployment. If successful, the task
  information will be tracked until the task has completed. This function
  returns a map containing the task information which can be used to
  retrieve the task and track the deployment."
  [region application-name params]
  (when-let [application (application application-name)]
    (let [{:keys [headers status]} (http/simple-post (region-deploy-url region) {:query-params params :follow-redirects false})]
      (when (= 302 status)
        (let [{:strs [location]} headers
              task-info (task-info-from-url location)
              {:keys [runId workflowId]} task-info]
          (schedule-track-task region runId workflowId (* 1 60 60))
          task-info)))))

;(deploy "eu-west-1" "skeleton")
;(last-launch-configuration-name "eu-west-1" "skeleton")
;(last-security-groups "eu-west-1" "skeleton")
