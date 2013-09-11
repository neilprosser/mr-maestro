(ns exploud.asgard
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [environ.core :refer [env]]
            [monger.collection :as mc]
            [overtone.at-at :as at-at]))

(def tp (at-at/mk-pool))

(def asgard-url
  (env :service-asgard-url))

(defn- application-url [application-name]
  (str asgard-url "/application/show/" application-name ".json"))

(defn- cluster-url [region cluster-name]
  (str asgard-url "/" region "/cluster/show/" cluster-name ".json"))

(defn- task-url [region run-id workflow-id]
  (str asgard-url "/" region "/task/show.json" {:query-params {"runId" run-id "workflowId" workflow-id}}))

(defn- region-deploy-url [region]
  (str asgard-url "/" region "/cluster/deploy"))

(defn- simple-get [url]
  (http/get url {:throw-exceptions false}))

(defn- simple-post [url params]
  (http/post url (merge-with + params) {:throw-exceptions false}))

(defn application
  "Retrieves information about an application from Asgard"
  [application-name]
  (let [response (simple-get (application-url application-name))
        status (:status response)]
    (when (= status 200)
      (json/parse-string (:body response) true))))

(defn cluster
  "Retrieves information about a cluster from Asgard"
  [region cluster-name]
  (let [response (simple-get (cluster-url region cluster-name))
        status (:status response)]
    (when (= status 200)
      (json/parse-string (:body response) true))))

(defn task [region run-id workflow-id]
  "Retrives information about a task from Asgard"
  (let [{:keys [body status]} (simple-get (task-url region run-id workflow-id))]
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

(defn- schedule-track-task [region run-id workflow-id]
  (at-at/after 1000 #(track-task region run-id workflow-id) tp :desc (task-tracking-desc region run-id workflow-id)))

(defn- track-task [region run-id workflow-id]
  (let [desc (task-tracking-desc region run-id workflow-id)]
    (when-let [task (task region run-id workflow-id)]
      (mc/update "tasks" {:region region :run-id run-id :workflow-id workflow-id} (merge-with + {:region region :run-id run-id :workflow-id workflow-id} task) :upsert true)
      (when-not (finished? task)
        (schedule-track-task region run-id workflow-id)))))

(defn- email [application]
  (:email (:app application)))

(def ami-id "ami-df8f95ab")

(def vpc-id "vpc-7bc88713")

(defn- task-info-from-url [url]
  (into {} (map #(let [[k v] %] {(keyword k) v}) (map #(clojure.string/split % #"=") (clojure.string/split (nth (clojure.string/split url #"\?" 2) 1) #"&")))))

(defn deploy-for-great-success
  "Kicks off an automated red/black deployment with some standard parameters
  (for now). If successful, the task information will be tracked until the task
  has completed. This function returns a map containing the task information
  which can be used to retrieve the task and track the deployment."
  [region application-name]
  (when-let [application (application application-name)]
    (let [params [["azRebalance" "enabled"]
                  ["canaryAssessmentDurationMinutes" 60]
                  ["canaryCapacity" 1]
                  ["canaryStartUpTimeoutMinutes" 30]
                  ["clusterName" "skeleton"]
                  ["defaultCooldown" 10]
                  ["delayDurationMinutes" 0]
                  ["deletePreviousAsg" "Ask"]
                  ["desiredCapacity" 1]
                  ["desiredCapacityAssesmentDurationMinutes" 5]
                  ["desiredCapacityStartUpTimeoutMinutes" 5]
                  ["disablePreviousAsg" "Ask"]
                  ["doCanary" false]
                  ["fullTrafficAssessmentDurationMinutes" 5]
                  ["healthCheckGracePeriod" 600]
                  ["healthCheckType" "EC2"]
                  ["iamInstanceProfile" application-name]
                  ["imageId" ami-id]
                  ["instanceType" "t1.micro"]
                  ["kernelId" ""]
                  ["keyName" "nprosser-key"]
                  ["max" 1]
                  ["min" 1]
                  ["name" application-name]
                  ["noOptionalDefaults" true]
                  ["notificationDestination" (email application)]
                  ["pricing" "ON_DEMAND"]
                  ["ramdiskId" ""]
                  ["region" region]
                  ["scaleUp" "Ask"]
                  [(str "selectedLoadBalancersForVpcId" vpc-id) application-name]
                  ["selectedSecurityGroups" "sg-c453b4ab"]
                  ["selectedSecurityGroups" "sg-ef9e7680"]
                  ["selectedZones" (str region "a")]
                  ["selectedZones" (str region "b")]
                  ["subnetPurpose" "internal"]
                  ["terminationPolicy" "Default"]
                  ["ticket" ""]]
          {:keys [headers status]} (simple-post (region-deploy-url region) {:query-params params :follow-redirects false})]
      (when (= 302 status)
        (let [{:strs [location]} headers
              task-info (task-info-from-url location)
              {:keys [runId workflowId]} task-info]
          (schedule-track-task region runId workflowId)
          task-info)))))

;(deploy-for-great-success "eu-west-1" "skeleton")
