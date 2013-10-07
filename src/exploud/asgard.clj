(ns exploud.asgard
  (:require [cheshire.core :as json]
            [clj-time.format :as fmt]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [exploud.http :as http]
            [exploud.store :as store]
            [overtone.at-at :as at-at]))

(def tp (at-at/mk-pool))

(def vpc-id
  (env :service-vpc-id))

(def asgard-date-formatter (fmt/formatter "YYYY-MM-dd_HH:mm:ss"))
(def date-formatter (fmt/formatters :date-time))

(def default-deploy-params
  {"azRebalance" "enabled"
   "canaryAssessmentDurationMinutes" 60
   "canaryCapacity" 1
   "canaryStartUpTimeoutMinutes" 30
   "defaultCooldown" 10
   "delayDurationMinutes" 0
   "deletePreviousAsg" "Yes"
   "desiredCapacity" 1
   "desiredCapacityAssesmentDurationMinutes" 5
   "desiredCapacityStartUpTimeoutMinutes" 5
   "disablePreviousAsg" "Yes"
   "doCanary" false
   "fullTrafficAssessmentDurationMinutes" 5
   "healthCheckGracePeriod" 600
   "healthCheckType" "EC2"
   "instanceType" "t1.micro"
   "kernelId" ""
   "keyName" "nprosser-key"
   "max" 1
   "min" 1
   "noOptionalDefaults" true
   "pricing" "ON_DEMAND"
   "ramdiskId" ""
   "scaleUp" "Yes"
   "subnetPurpose" "internal"
   "terminationPolicy" "Default"})

(def required-keys
  ["azRebalance"
   "defaultCooldown"
   "desiredCapacity"
   "healthCheckGracePeriod"
   "healthCheckType"
   "iamInstanceProfile"
   "imageId"
   "instanceType"
   "kernelId"
   "keyName"
   "max"
   "min"
   "pricing"
   "ramdiskId"
   "selectedLoadBalancers"
   "selectedSecurityGroups"
   "selectedZones"
   "subnetPurpose"
   "terminationPolicy"
   "ticket"
   "_action_save"])

(def asgard-url
  (env :service-asgard-url))

(defn- application-url [region application-name]
  (str asgard-url "/" region "/application/show/" application-name ".json"))

(defn- upsert-application-url []
  (str asgard-url "/application/index"))

(defn- application-list-url []
  (str asgard-url "/application/list.json"))

(defn- cluster-url [region cluster-name]
  (str asgard-url "/" region "/cluster/show/" cluster-name ".json"))

(defn- auto-scaling-save-url [region]
  (str asgard-url "/" region "/autoScaling/save"))

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
  [region application-name]
  (let [response (http/simple-get (application-url region application-name))
        {:keys [status]} response]
    (when (= status 200)
      (json/parse-string (:body response) true))))

(defn application-list
  "Retrieves the list of applications from Asgard"
  []
  (let [response (http/simple-get (application-list-url))
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
  (update-in task [:log] (fn [log] (map split-log-message log))))

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
  (letfn [(reschedule [] (schedule-track-task region run-id workflow-id (dec count)))]
    (try
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
                (reschedule))
              (log/info "Task" region run-id workflow-id "is stopping being tracked")))
          (log/info "No task information for" region run-id workflow-id)))
      (catch Exception e
        (log/warn e)
        (reschedule)))))

(defn- task-info-from-url [url]
  (into {} (map (fn [[k v]] {(keyword k) (ring.util.codec/url-decode v)}) (map #(clojure.string/split % #"=") (clojure.string/split (nth (clojure.string/split url #"\?" 2) 1) #"&")))))

(defn applications []
  (let [application-list (application-list)]
    (map :name application-list)))

(defn upsert-application [application-name {:keys [description email owner]}]
  (http/simple-post (upsert-application-url) {:form-params {:description description
                                                            :email email
                                                            :monitorBucketType "application"
                                                            :name application-name
                                                            :owner owner
                                                            :ticket ""
                                                            :type "Web Service"
                                                            :_action_update ""}
                                              :follow-redirects false}))

(defn auto-scaling-group-exists? [region application-name]
  (let [application (application region application-name)]
    (pos? (count (:groups application)))))

(defn- create-asgard-params [params]
  (for [[k v] (seq params)
        vs (flatten (conj [] v))]
    [k vs]))

(defn- replace-load-balancer-param [params]
  (if (= "internal" (get params "subnetPurpose"))
    (set/rename-keys params {"selectedLoadBalancers" (str "selectedLoadBalancersForVpcId" vpc-id)})
    params))

(defn- is-security-group-name? [security-group]
  (re-find #"^sg-" security-group))

(defn- get-security-group-id [security-group]
  "hello")

(defn- replace-security-group-names [params]
  (if-let [security-group-names (get params "selectedSecurityGroups")]
    (map (fn [sg] (if (is-security-group-name? sg)
                   (get-security-group-id sg)
                   sg))
         security-group-names)
    params))

(defn- amend-params [params]
  (comment (-> (replace-load-balancer-param params)
               (replace-security-group-names params)))
  params)

(defn- create-manual-deploy-params [params region application-name]
  (let [additional-params {"appName" application-name
                           "appWithClusterOptLevel" false
                           "countries" ""
                           "detail" ""
                           "devPhase" ""
                           "hardware" ""
                           "newStack" ""
                           "partners" ""
                           "revision" ""
                           "stack" ""
                           "region" region}
        required-params (amend-params (select-keys params required-keys))]
    (merge additional-params required-params)))

(defn- application-params [application-name]
  {"clusterName" application-name
   "iamInstanceProfile" application-name
   "name" application-name})

(defn manual-deploy
  "In the event that an application doesn't have an auto-scaling group for
  us to copy we use a manual creation of that ASG to get going."
  [region application-name params]
  (let [all-params (merge default-deploy-params (application-params application-name) params)]
    (log/info "Application" application-name "is being manually deployed in" region "with params" all-params)
    (let [merged-params (create-manual-deploy-params all-params region application-name)]
      (when-let [application (application region application-name)]
        (let [{:keys [headers status]} (http/simple-post (auto-scaling-save-url region) {:form-params (create-asgard-params merged-params) :follow-redirects false :socket-timeout 30000})]
          (when (= 302 status)
            (log/info "Headers are" headers)))))))

(defn auto-deploy
  "Kicks off an automated red/black deployment. If successful, the task
  information will be tracked until the task has completed. This function
  returns a map containing the task information which can be used to
  retrieve the task and track the deployment."
  [region application-name params]
  (let [all-params (merge default-deploy-params (application-params application-name) params)]
    (log/info "Application" application-name "is being automatically deployed in" region "with params" all-params)
    (when-let [application (application region application-name)]
      (let [{:keys [headers status]} (http/simple-post (region-deploy-url region) {:form-params (create-asgard-params all-params) :follow-redirects false :socket-timeout 30000})]
        (when (= 302 status)
          (log/info "Headers are" headers)
          (let [{:strs [location]} headers
                task-info (task-info-from-url location)
                {:keys [runId workflowId]} task-info
                task-id (store/store-task {:region region :runId runId :workflowId workflowId})]
            (schedule-track-task region runId workflowId (* 1 60 60))
            {:taskId task-id}))))))

;(task "eu-west-1" "120DqQWqEBYsOK5Vaj6sK8b4YErobn8sF61FcN7OA63t0=" "b7287ace-cfd3-41b6-9618-189534d9f207")
;(last-launch-configuration-name "eu-west-1" "skeleton")
;(last-security-groups "eu-west-1" "skeleton")
;(task-info-from-url "http://asgard.brislabs.com:8080/task/show?runId=12lyXMu%2FrpdVtZ%2FpgbUQ7hpuAEzizQ9QKa0fnpj7G8fwE%3D&workflowId=a99a149d-be24-47c8-93c0-d10e00334dee")
;(application "eu-west-1" "missing")
;(upsert-application "somethingnew" {:description "Updated from Exploud" :email "neil.prosser@gmail.com" :owner "someone"})
;(applications)
;(application-list)
;(application "eu-west-1" "skeleton")
;(auto-scaling-group-exists? "eu-west-1" "skeleton")
;(create-asgard-params (create-manual-deploy-params (merge (get (exploud.tyranitar/deployment-params "dev" "skeleton" "HEAD") "data") {"imageId" "woooooo" "selectedZones" ["eu-west-1a" "eu-west-1b"] "ticket" "something" "_action_save" ""}) "eu-west-1" "skeleton"))
;(auto-scaling-save-url "eu-west-1")
;(set/rename-keys {"selectedLoadBalancers" ["beef" "steak"]} {"selectedLoadBalancers" "selectedLoadBalancersForVpcIdvpc-e23232"})
;(replace-security-group-names {"selectedSecurityGroups" ["woo" "sg-1234"]})
