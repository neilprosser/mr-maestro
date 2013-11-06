(ns exploud.asgard
  "## Integration with Asgard

   We integrate with a few parts of [Asgard](https://github.com/Netflix/asgard).
   It's all good, at least until we have to switch to Azure.

   Asgard introduces a few concepts on top of AWS:

   - __Application__ - An application is a simple construct which pulls together
     a name, owner and an email address to send notifications about it.
   - __Cluster__ - A cluster is the collection of Auto Scaling Groups which form
     an application.
   - __Stack__ - A stack is a way of extending the naming conventions which
     Asgard uses to allow many groupings. We use it in our case as a synonym for
     environment."
  (:require [cheshire.core :as json]
            [clj-time.format :as fmt]
            [clojure
             [set :as set]
             [string :as str]]
            [clojure.tools.logging :as log]
            [dire.core :refer [with-handler!
                               with-post-hook!
                               with-pre-hook!
                               with-precondition!]]
            [environ.core :refer [env]]
            [exploud
             [http :as http]
             [store :as store]
             [tasks :as tasks]
             [util :as util]]
            [overtone.at-at :as at-at]))

;; # General def-age

(def vpc-id
  "The VPC ID we'll be deploying into."
  (env :service-vpc-id))

(def asgard-log-date-formatter
  "The formatter used by Asgard in log messages."
  (fmt/formatter "YYYY-MM-dd_HH:mm:ss"))

(def asgard-update-time-formatter
  "The formatter used by Asgard in its `updateTime` field. Why would you want to
   use the same format in two places?"
  (fmt/formatter "YYYY-MM-dd HH:mm:ss z"))

(def task-track-count
  "The number of seconds we'll keep tracking a task for, before giving up."
  (* 1 60 60))

(def default-key-name
  "The default SSH key we'll use on the instances that are launched. This key
   will be replaced by our usual security measures so isn't really useful."
  "nprosser-key")

(def all-create-new-asg-keys
  "A set of the all parameters we can provide to Asgard when creating a new
   ASG."
  #{:_action_save
    :appName
    :appWithClusterOptLevel
    :azRebalance
    :countries
    :defaultCooldown
    :desiredCapacity
    :detail
    :devPhase
    :hardware
    :healthCheckGracePeriod
    :healthCheckType
    :iamInstanceProfile
    :imageId
    :instanceType
    :kernelId
    :keyName
    :max
    :min
    :partners
    :pricing
    :ramdiskId
    :requestedFromGui
    :revision
    :selectedLoadBalancers
    :selectedSecurityGroups
    :selectedZones
    :stack
    :subnetPurpose
    :terminationPolicy
    :ticket})

(def all-create-next-asg-keys
  "A set of all the parameters we can provide to Asgard when creating the next
   ASG for an application."
  #{:_action_createNextGroup
    :afterBootWait
    :azRebalance
    :defaultCooldown
    :desiredCapacity
    :healthCheckGracePeriod
    :healthCheckType
    :iamInstanceProfile
    :imageId
    :instanceType
    :kernelId
    :keyName
    :max
    :min
    :name
    :noOptionalDefaults
    :pricing
    :ramdiskId
    :selectedLoadBalancers
    :selectedSecurityGroups
    :selectedZones
    :subnetPurpose
    :terminationPolicy
    :ticket
    :trafficAllowed})

;; # Concerning the creation of default Asgard parameter maps
;;
;; When combining these parameters for a deployment we'll take the defaults and
;; merge them with the users parameters, letting the user parameters override
;; the defaults. We'll then overlay the protected parameters for the operation
;; back over the top.

(def default-shared-parameters
  "A map of the default parameters shared by both creating a new ASG and
   creating the next ASG."
  {:azRebalance "enabled"
   :defaultCooldown 10
   :desiredCapacity 1
   :healthCheckGracePeriod 600
   :healthCheckType "EC2"
   :instanceType "t1.micro"
   :kernelId ""
   :max 1
   :min 1
   :pricing "ON_DEMAND"
   :ramdiskId ""
   :selectedLoadBalancers nil
   :selectedSecurityGroups nil
   :selectedZones ["a" "b"]
   :subnetPurpose "internal"
   :terminationPolicy "Default"})

(def default-create-new-asg-parameters
  "A map of the default parameters we use when creating a new ASG so the user
   doesn't always have to provide everything."
  (merge default-shared-parameters
         {:appWithClusterOptLevel false
          :countries ""
          :detail ""
          :devPhase ""
          :hardware ""
          :partners ""
          :requestedFromGui true
          :revision ""}))

(defn protected-create-new-asg-parameters
  "Creates a map of the parameters we populate ourselves and won't let the user
   override when creating a new ASG."
  [application-name environment image-id ticket-id]
  {:_action_save ""
   :appName application-name
   :iamInstanceProfile application-name
   :imageId image-id
   :keyName default-key-name
   :stack environment
   :ticket ticket-id})

(def default-create-next-asg-parameters
  "A map of the default parameters we use when creating the next ASG for an
   application so the user doesn't always have to provide everything."
  (merge default-shared-parameters
         {:afterBootWait 30
          :noOptionalDefaults true}))

(defn protected-create-next-asg-parameters
  "Creates a map of the parameters we populate ourselves and won't let the user
   override when creating the next ASG for an application."
  [application-name environment image-id ticket-id]
  {:_action_createNextGroup ""
   :iamInstanceProfile application-name
   :imageId image-id
   :keyName default-key-name
   :name (str application-name "-" environment)
   :ticket ticket-id
   :trafficAllowed ""})

;; # Concerning Asgard URL generation

(def asgards-by-environment
  "A map of Asgards by the environment they serve."
  {:poke (env :service-dev-asgard-url)
   :prod (env :service-prod-asgard-url)})

(defn asgard-url-for-environment
  "The URL describing the Asgard to use for a given environment."
  [environment]
  ((keyword environment) asgards-by-environment (:poke asgards-by-environment)))

(def asgard-url
  (:poke asgards-by-environment))

(defn- application-url
  "Gives us a region-based URL we can use to get information about an
   application."
  [region application-name]
  (str asgard-url "/" region "/application/show/" application-name ".json"))

(defn- application-list-url
  "Gives us a URL we can use to retrieve the list of applications."
  []
  (str asgard-url "/application/list.json"))

(defn- auto-scaling-group-url
  "Gives us a region-based URL we can use to get information about an Auto
   Scaling Group."
  [region asg-name]
  (str asgard-url "/" region "/autoScaling/show/" asg-name ".json"))

(defn- auto-scaling-save-url
  "Gives us a region-based URL we can use to save Auto Scaling Groups."
  [region]
  (str asgard-url "/" region "/autoScaling/save"))

(defn- cluster-url
  "Gives us a region-based URL we can use to get information about a Cluster"
  [region cluster-name]
  (str asgard-url "/" region "/cluster/show/" cluster-name ".json"))

(defn- cluster-create-next-group-url
  "Gives us a region-based URL we can use to create the next Auto Scaling
   Group."
  [region]
  (str asgard-url "/" region "/cluster/createNextGroup"))

(defn- cluster-index-url
  "Gives us a region-based URL we can use to make changes to Auto Scaling
   Groups."
  [region]
  (str asgard-url "/" region "/cluster/index"))

(defn- image-url
  "Gives us a region-based URL we can use to get information about an image."
  [region image-id]
  (str asgard-url "/" region "/image/show/" image-id ".json"))

(defn- instances-list-url
  "Gives us a region-based URL we can use to get a list of all instances."
  [region]
  (str asgard-url "/" region "/instance/list.json"))

(defn- instance-url
  "Gives us a region-based URL we can use to get information about an instance."
  [region instance-id]
  (str asgard-url "/" region "/instance/show/" instance-id ".json"))

(defn- load-balancer-url
  "Gives us a region-based URL we can use to get information about a load-
   balancer"
  [region elb-name]
  (str asgard-url "/" region "/loadBalancer/show/" elb-name ".json"))

(defn- security-groups-list-url
  "Gives us a region-based URL we can use to get a list of all Security
   Groups."
  [region]
  (str asgard-url "/" region "/security/list.json"))

(defn- tasks-url
  "Gives us a region-based URL we can use to get tasks."
  []
  (str asgard-url "/task/list.json"))

(defn- task-by-id-url
  "Gives us a region-based URL we can use to get a single task by its ID."
  [region task-id]
  (str asgard-url "/" region "/task/show/" task-id ".json"))

(defn- upsert-application-url
  "Gives us a URL we can use to upsert an application."
  []
  (str asgard-url "/application/index"))

(defn- launch-config-list-url
  "Gives us a URL to get a list of all launch configurations."
  [region]
  (str asgard-url "/" region "/launchConfiguration/list.json"))

(defn- launch-config-url
  "Gives us a URL to get the details of the launch config with the given ID."
  [region config-id]
  (str asgard-url "/" region "/launchConfiguration/show/" config-id ".json"))

;; # Task transformations

(defn split-log-message
  "Splits a log message from its Asgard form (where each line is something like
   `2013-10-11_18:25:23 Completed in 1s.`) to a map with separate `:date` and
   `:message` fields."
  [log]
  (let [[date message] (clojure.string/split log #" " 2)]
    {:date (fmt/parse asgard-log-date-formatter date) :message message}))

(defn split-log-messages
  "If `task` contains a `:log` then attempt to split everything in it. Returns
   either the amended task or the original if nothing was found."
  [{:keys [log] :as task}]
  (if log
    (assoc task :log (map split-log-message log))
    task))

(defn correct-date-time
  "Parses a task date/time from its Asgard form (like `2013-10-11 14:20:42 UTC`)
   to an ISO8601 one. Unfortunately has to do a crappy string-replace of `UTC`
   for `GMT`, ugh..."
  [date]
  (fmt/parse asgard-update-time-formatter (str/replace date "UTC" "GMT")))

(defn correct-update-time
  "If `task` contains an `:updateTime` then correct the date. Returns either the
   amended task or the original if nothing was found."
  [{:keys [updateTime] :as task}]
  (if updateTime
    (assoc task :updateTime (correct-date-time updateTime))
    task))

(defn munge-task
  "Converts an Asgard task to a desired form by using `split-log-message` on
   each line of the `:log` in the task and replacing it."
  [task]
  (-> task
      split-log-messages
      correct-update-time))

;; Pre-hook attached to `munge-task` to log parameters.
(with-pre-hook! #'munge-task
  (fn [t]
    (log/debug "Munging task" t)))

;; Post-hook attached to `munge-task` to log its return value.
(with-post-hook! #'munge-task
  (fn [t]
    (log/debug "Task was munged to" t)))

;; # Concerning grabbing objects from Asgard

(defn application
  "Retrieves information about an application from Asgard."
  [region application-name]
  (let [{:keys [status body]} (http/simple-get (application-url
                                                region
                                                application-name))]
    (when (= status 200)
      (json/parse-string body true))))

(defn applications
  "Retrieves the list of applications from Asgard."
  []
  (let [{:keys [status body]} (http/simple-get (application-list-url))]
    (when (= status 200)
      (json/parse-string body true))))

(defn auto-scaling-group
  "Retrieves information about an Auto Scaling Group from Asgard."
  [region asg-name]
  (let [{:keys [status body]} (http/simple-get (auto-scaling-group-url
                                                region
                                                asg-name))]
    (when (= status 200)
      (json/parse-string body true))))

(defn cluster
  "Retrieves information about a Cluster from Asgard."
  [region cluster-name]
  (let [{:keys [status body]} (http/simple-get (cluster-url
                                                region
                                                cluster-name))]
    (when (= status 200)
      (json/parse-string body true))))

(defn image
  "Retrieves information about an image from Asgard."
  [region image-id]
  (let [{:keys [status body]} (http/simple-get (image-url region image-id))]
    (when (= status 200)
      (json/parse-string body true))))

(defn instance
  "Retrieves information about an instance, or `nil` if it doesn't exist."
  [region instance-id]
  (let [{:keys [body status]} (http/simple-get (instance-url
                                                region instance-id))]
    (when (= status 200)
      (json/parse-string body true))))

(defn all-instances
  "Retrieves information about all instances in the given region."
  [region]
  (let [{:keys [body status]} (http/simple-get (instances-list-url region))]
    (when (= status 200)
      (json/parse-string body true))))

(defn instances-in-asg
  "Retrieves a list of instances in an ASG, or `nil` if the ASG doesn't exist."
  [region asg-name]
  (when-let [asg (auto-scaling-group region asg-name)]
    (let [instances (get-in asg [:group :instances])]
      (map (fn [i] (instance region (:instanceId i))) instances))))

(defn last-auto-scaling-group
  "Retrieves the last ASG for a cluster, or `nil` if one doesn't exist."
  [region cluster-name]
  (last (cluster region cluster-name)))

(defn launch-config
  "Fetches the launch configuration with the given ID in the given region."
  [region config-id]
  (let [{:keys [body status]} (http/simple-get (launch-config-url region config-id))]
    (when (= status 200)
      (json/parse-string body true))))

(defn launch-config-list
  "Fetches all launch configs for the given region."
  [region]
  (let [{:keys [body status]} (http/simple-get (launch-config-list-url region))]
    (when (= status 200)
      (json/parse-string body true))))

(defn load-balancer
  "Retrieves information about a load-balancer."
  [region elb-name]
  (let [{:keys [body status]} (http/simple-get (load-balancer-url
                                                region elb-name))]
    (when (= status 200)
      (json/parse-string body true))))

(defn security-groups
  "Retrieves all security groups within a particular region."
  [region]
  (let [{:keys [body status]} (http/simple-get (security-groups-list-url
                                                region))]
    (when (= status 200)
      (:securityGroups (json/parse-string body true)))))

(defn task-by-url
  "Retrieves a task by its URL."
  [task-url]
  (let [{:keys [body status]} (http/simple-get task-url)]
    (when (= status 200)
      (munge-task (json/parse-string body true)))))

(defn tasks
  "Retrieves all tasks that Asgard knows about. It will combine
   `:runningTaskList` with `:completedTaskList` (with the running tasks first in
   the list)."
  []
  (let [{:keys [body status]} (http/simple-get (tasks-url))]
    (when (= status 200)
      (let [both (json/parse-string body true)]
        (concat (:runningTaskList both)
                (:completedTaskList both))))))

;; # Concerning updating things in Asgard

(defn upsert-application
  "Updates the information on an application in Asgard. This function will
   replace an already existing application or create a new one."
  [application-name {:keys [description email owner]}]
  (http/simple-post (upsert-application-url)
                    {:form-params {:description description
                                   :email email
                                   :monitorBucketType "application"
                                   :name application-name
                                   :owner owner
                                   :ticket ""
                                   :type "Web Service"
                                   :_action_update ""}
                     :follow-redirects false}))

;; # Concerning parameter transformation

(defn remove-nil-values
  "Removes any key-value pairs from `map` if the value `nil`."
  [map]
  (into {} (filter (fn [[k v]] (not (nil? v))) (seq map))))

(defn replace-load-balancer-key
  "If `:subnetPurpose` is `internal` and `:selectedLoadBalancers` is found
   within `parameters` the key name will be switched with
   `:selectedLoadBalancersForVpcId{vpc-id}"
  [parameters]
  (if (and (= "internal" (:subnetPurpose parameters))
           (:selectedLoadBalancers parameters))
    (set/rename-keys parameters
                     {:selectedLoadBalancers
                      (keyword (str "selectedLoadBalancersForVpcId" vpc-id))})
    parameters))

(defn is-security-group-id?
  "Whether `security-group` starts with `sg-`"
  [security-group]
  (re-find #"^sg-" security-group))

(defn get-security-group-id
  "Gets the ID of a security group with the given name in a particular region."
  [security-group region]
  (let [security-groups (security-groups region)]
    (if-let [found-group (first (filter (fn [sg] (= security-group
                                                   (:groupName sg)))
                                        security-groups))]
      (:groupId found-group)
      (throw (ex-info "Unknown security group name"
                      {:type ::unknown-security-group
                       :name security-group
                       :region region})))))

(defn replace-security-group-name
  "If `security-group` looks like it's a security name, it'll be switched with
   its ID."
  [region security-group]
  (if (is-security-group-id? security-group)
    security-group
    (get-security-group-id security-group region)))

(defn replace-security-group-names
  "If `:subnetPurpose` is `internal` and `:securityGroupNames` is found within
   `parameters` the value will be checked for security group names and replaced
   with their IDs (since we can't use security group names in a VPC)."
  [parameters region]
  (if (= "internal" (:subnetPurpose parameters))
    (if-let [security-group-names (util/list-from
                                   (:selectedSecurityGroups parameters))]
      (let [security-group-ids (map (fn [sg]
                                      (replace-security-group-name region sg))
                                    security-group-names)]
        (assoc parameters :selectedSecurityGroups security-group-ids))
      parameters)
    parameters))

(defn add-exploud-security-group
  "Make sure `:selectedSecurityGroups` contains the ID of the
   `exploud-healthcheck` security group. This group will be used to allow
   exploud to talk to the box and check its healthcheck."
  [parameters region]
  (let [exploud-group-id (replace-security-group-name region
                                                      "exploud-healthcheck")]
    (if-let [groups (:selectedSecurityGroups parameters)]
      (assoc parameters :selectedSecurityGroups (conj groups exploud-group-id))
      (assoc parameters :selectedSecurityGroups (vector exploud-group-id)))))

(defn add-region-to-zones
  "Replace any availability zones found in `:selectedZones` and replace them
   with the region and the zone. For example if using region `eu-west-1` and we
   encounter `a` in `:selectedZones` then we'd replace it with `eu-west-1a`,
   capiche?"
  [parameters region]
  (if-let [zones (:selectedZones parameters)]
    (let [full-zones (map (fn [z] (str region z)) (util/list-from zones))]
      (assoc parameters :selectedZones full-zones))
    parameters))

(defn prepare-parameters
  "Prepares Asgard parameters by running them through a series of
   transformations."
  [parameters region]
  (-> parameters
      remove-nil-values
      replace-load-balancer-key
      (replace-security-group-names region)
      (add-exploud-security-group region)
      (add-region-to-zones region)))

(defn explode-parameters
  "Take a map of parameters and turns them into a list of [key value] pairs
   where the same key may appear multiple times. This is used to create the
   form parameters which we pass to Asgard (and may be specified multiple times
   each)."
  [parameters]
  (for [[k v] (seq parameters)
        vs (flatten (vector v))]
    [(name k) vs]))

;; # Concerning tracking tasks

(def finished-states
  "The states at which a task is deemed finished."
  #{"completed" "failed" "terminated"})

(defn finished?
  "Indicates whether the task is completed."
  [task]
  (contains? finished-states (:status task)))

;; We're going to need this in a minute.
(declare track-task)

(defn track-until-completed
  "After a 1s delay, tracks the task by saving its content to the task store
   until it is completed (as indicated by `finished?`) or `count` reaches 0."
  [ticket-id {:keys [url] :as task} count completed-fn timed-out-fn]
  (at-at/after 1000 #(track-task ticket-id task count completed-fn timed-out-fn)
               tasks/pool :desc (str "task-" url)))

;; Pre-hook attached to `track-until-completed` to log the parameters.
(with-pre-hook! #'track-until-completed
  (fn [ticket-id task count completed-fn timed-out-fn]
    (log/debug "Scheduling tracking for" {:ticket-id ticket-id
                                          :task task
                                          :count count
                                          :completed-fn completed-fn
                                          :timed-out-fn timed-out-fn})))

(defn track-task
  "Grabs the task by its URL and updates its details in the store. If it's not
   completed and we've not exhausted the retry count it'll reschedule itself."
  [ticket-id {:keys [url] :as task} count completed-fn timed-out-fn]
  (try
    (if-let [asgard-task (task-by-url url)]
      (let [task (merge task asgard-task)]
        (store/store-task ticket-id task)
        (cond (finished? asgard-task) (completed-fn ticket-id task)
              (zero? count) (timed-out-fn ticket-id task)
              :else (track-until-completed ticket-id task (dec count)
                                           completed-fn timed-out-fn)))
      (throw (ex-info "No task found" {:type ::task-missing :url url})))
    (catch Exception e
      (do
        (log/error "Caught exception" e (map str (.getStackTrace e)))
        (throw e)))))

;; Pre-hook attached to `track-task` to log the parameters.
(with-pre-hook! #'track-task
  (fn [ticket-id task count completed-fn timed-out-fn]
    (log/debug "Tracking" {:ticket-id ticket-id
                           :task task
                           :count count
                           :completed-fn completed-fn
                           :timed-out-fn timed-out-fn})))

;; Handler for recovering from failure while tracking a task. In the event of an
;; exception marked with a `:class` of `:http` or `:store` we'll reschedule.
(with-handler! #'track-task
  clojure.lang.ExceptionInfo
  (fn [e ticket-id task count completed-fn timed-out-fn]
    (let [data (.getData e)]
      (if (or (= (:class data) :http)
              (= (:class data) :store))
        (track-until-completed ticket-id task (dec count)
                               completed-fn timed-out-fn)
        (throw e)))))

;; # Concerning deleting ASGs

(defn delete-asg
  "Begins a delete operation on the specified Auto Scaling Group in the region
   given. Will start tracking the resulting task URL until completed. You can
   assume that a non-explosive call has been successful and the task is being
   tracked."
  [region asg-name ticket-id task completed-fn timed-out-fn]
  (let [parameters {:_action_delete ""
                    :name asg-name
                    :ticket ticket-id}
        {:keys [status headers] :as response} (http/simple-post
                                               (cluster-index-url region)
                                               {:form-params (explode-parameters
                                                              parameters)})]
    (if (= status 302)
      (track-until-completed ticket-id
                             (merge task {:url
                                          (str (get headers "location") ".json")
                                          :asgardParameters parameters})
                             task-track-count completed-fn timed-out-fn)
      (throw (ex-info "Unexpected status while deleting ASG"
                      {:type ::unexpected-response
                       :response response})))))

;; Precondition attached to `delete-asg` asserting that the ASG we're
;; attempting to delete exists.
(with-precondition! #'delete-asg
  :asg-exists
  (fn [r a _ _ _ _]
    (auto-scaling-group r a)))

;; Handler for a missing ASG attached to `delete-asg`.
(with-handler! #'delete-asg
  {:precondition :asg-exists}
  (fn [e & args] (throw (ex-info "Auto Scaling Group does not exist."
                                 {:type ::missing-asg
                                  :args args}))))

;; # Concerning resizing ASGs

(defn resize-asg
  "Begins a resize operation on the specified Auto Scaling Group in the region
   given. Will start tracking the resulting task URL until completed. You can
   assume that a non-explosive call has been successful and the task is being
   tracked."
  [region asg-name ticket-id task new-size completed-fn timed-out-fn]
  (let [parameters {:_action_resize ""
                    :minAndMaxSize new-size
                    :name asg-name
                    :ticket ticket-id}
        {:keys [status headers] :as response} (http/simple-post
                                               (cluster-index-url region)
                                               {:form-params (explode-parameters
                                                              parameters)})]
    (if (= status 302)
      (track-until-completed ticket-id
                             (merge task {:url
                                          (str (get headers "location") ".json")
                                          :asgardParameters parameters})
                             task-track-count completed-fn timed-out-fn)
      (throw (ex-info "Unexpected status while resizing ASG"
                      {:type ::unexpected-response
                       :response response})))))

;; Precondition attached to `resize-asg` asserting that the ASG we're attempting
;; to resize exists.
(with-precondition! #'resize-asg
  :asg-exists
  (fn [r a _ _ _ _ _]
    (auto-scaling-group r a)))

;; Handler for a missing ASG attached to `resize-asg`.
(with-handler! #'resize-asg
  {:precondition :asg-exists}
  (fn [e & args] (throw (ex-info "Auto Scaling Group does not exist."
                                 {:type ::missing-asg
                                  :args args}))))

;; # Concerning enabling traffic for ASGs

(defn enable-asg
  "Begins an enable traffic operation on the specified Auto Scaling Group in the
   region given. Will start tracking the resulting task URL until completed. You
   can assume that a non-explosive call has been successful and the task is
   being tracked."
  [region asg-name ticket-id task completed-fn timed-out-fn]
  (let [parameters {:_action_activate ""
                    :name asg-name
                    :ticket ticket-id}
        {:keys [status headers] :as response} (http/simple-post
                                               (cluster-index-url region)
                                               {:form-params (explode-parameters
                                                              parameters)})]
    (if (= status 302)
      (track-until-completed ticket-id
                             (merge task {:url
                                          (str (get headers "location") ".json")
                                          :asgardParameters parameters})
                             task-track-count completed-fn timed-out-fn)
      (throw (ex-info "Unexpected status while enabling ASG"
                      {:type ::unexpected-response
                       :response response})))))

;; Precondition attached to `enable-asg` asserting that the ASG we're attempting
;; to enable exists.
(with-precondition! #'enable-asg
  :asg-exists
  (fn [r a _ _ _ _]
    (auto-scaling-group r a)))

;; Handler for a missing ASG attached to `enable-asg`.
(with-handler! #'enable-asg
  {:precondition :asg-exists}
  (fn [e & args] (throw (ex-info "Auto Scaling Group does not exist."
                                 {:type ::missing-asg
                                  :args args}))))

;; # Concerning disabling traffic for ASGs

(defn disable-asg
  "Begins a disable traffic operation on the specified Auto Scaling Group in
   the region given. Will start tracking the resulting task URL until completed.
   You can assume that a non-explosive call has been successful and the task is
   being tracked."
  [region asg-name ticket-id task completed-fn timed-out-fn]
  (let [parameters {:_action_deactivate ""
                    :name asg-name
                    :ticket ticket-id}
        {:keys [status headers] :as response} (http/simple-post
                                               (cluster-index-url region)
                                               {:form-params (explode-parameters
                                                              parameters)})]
    (if (= status 302)
      (track-until-completed ticket-id
                             (merge task {:url
                                          (str (get headers "location") ".json")
                                          :asgardParameters parameters})
                             task-track-count completed-fn timed-out-fn)
      (throw (ex-info "Unexpected status while disabling ASG"
                      {:type ::unexpected-response
                       :response response})))))

;; Precondition attached to `disable-asg` asserting that the ASG we're
;; attempting to disable exists.
(with-precondition! #'disable-asg
  :asg-exists
  (fn [r a _ _ _ _]
    (auto-scaling-group r a)))

;; Handler for a missing ASG attached to `disable-asg`.
(with-handler! #'disable-asg
  {:precondition :asg-exists}
  (fn [e & args] (throw (ex-info "Auto Scaling Group does not exist."
                                 {:type ::missing-asg
                                  :args args}))))

;; # Concerning the creation of the first ASG for an application

(defn extract-new-asg-name
  "Extracts the new ASG name from what would be the `Location` header of the
   response when asking Asgard to create a new ASG. Takes something like
   `http://asgard/{region}/autoScaling/show/{asg-name}` and gives back
   `{asg-name}`."
  [url]
  (second (re-find #"/autoScaling/show/(.+)$" url)))

(defn create-new-asg-asgard-parameters
  "Creates the Asgard parameters for creating a new ASG as a combination of the
   various defaults and user-provided parameters."
  [region application-name environment image-id user-parameters ticket-id]
  (let [protected-parameters (protected-create-new-asg-parameters
                              application-name environment image-id ticket-id)]
    (prepare-parameters (merge default-create-new-asg-parameters
                               user-parameters protected-parameters) region)))

(defn create-new-asg
  "Begins a create new Auto Scaling Group operation for the specified
   application and environment in the region given. It __WILL__ start traffic to
   the newly-created ASG. Will start tracking the resulting task URL until
   completed. You can assume that a non-explosive call has been successful and
   the task is being tracked."
  [{:keys [ami application environment id parameters region task completed-fn timed-out-fn]}]
  (let [asgard-parameters (create-new-asg-asgard-parameters region
                                                            application
                                                            environment
                                                            ami
                                                            parameters
                                                            id)
        {:keys [status headers] :as response}
        (http/simple-post
         (auto-scaling-save-url region)
         {:form-params (explode-parameters asgard-parameters)})]
    (if (= status 302)
      (let [new-asg-name (extract-new-asg-name (get headers "location"))
            tasks (tasks)
            log-message (str "Create Auto Scaling Group '" new-asg-name "'")]
        (if-let [found-task
                 (first (filter (fn [t] (= (:name t) log-message)) tasks))]
          (let [task-id (:id found-task)
                url (task-by-id-url region task-id)]
            (store/add-to-deployment-parameters
             id
             {:newAutoScalingGroupName (str application "-" environment)})
            (track-until-completed
             id
             (merge task {:url url
                          :asgardParameters asgard-parameters})
             task-track-count
             completed-fn
             timed-out-fn))
          (throw (ex-info "No task found"
                          {:type ::task-missing
                           :tasks tasks
                           :log-message log-message})))
        new-asg-name)
      (throw (ex-info "Unexpected status while creating new ASG"
                      {:type ::unexpected-response
                       :response response})))))

;; # Concerning the creation of the next ASG for an application-name

(defn create-next-asg-asgard-parameters
  "Creates the Asgard parameters for creating the next ASG as a combination of
   the various defaults and user-provided parameters."
  [region application-name environment image-id user-parameters ticket-id]
  (let [protected-parameters (protected-create-next-asg-parameters
                              application-name environment image-id ticket-id)]
    (prepare-parameters (merge default-create-next-asg-parameters
                               user-parameters protected-parameters) region)))

(defn new-asg-name-from-task
  "Examines the `:message` of the first item in the task's `:log` for a string
   matching `Creating auto scaling group '{new-asg-name}'` and returns
   `new-asg-name`."
  [task-url]
  (let [{:keys [log]} (task-by-url task-url)
        first-log (first log)
        pattern #"Creating auto scaling group '([^']+)'"]
    (second (re-find pattern (:message first-log)))))

(defn create-next-asg
  "Begins a create next Auto Scaling Group operation for the specified
   application and environment in the region given. It __WILL NOT__ start
   traffic to the newly-created ASG. Will start tracking the resulting task URL
   until completed. You can assume that a non-explosive call has been successful
   and the task is being tracked."
  [{:keys [ami application environment id parameters region task completed-fn timed-out-fn]}]
  (let [asgard-parameters (create-next-asg-asgard-parameters
                           region
                           application
                           environment
                           ami
                           parameters
                           id)
        {:keys [status headers]
         :as response} (http/simple-post
                        (cluster-create-next-group-url region)
                        {:form-params (explode-parameters asgard-parameters)})]
    (if (= status 302)
      (let [task-json-url (str (get headers "location") ".json")
            new-asg-name (new-asg-name-from-task task-json-url)]
        (store/add-to-deployment-parameters id {:newAutoScalingGroupName new-asg-name})
        (track-until-completed id (merge task {:url task-json-url
                                               :asgardParameters asgard-parameters})
                               task-track-count completed-fn timed-out-fn))
      (throw (ex-info "Unexpected status while creating next ASG"
                      {:type ::unexpected-response
                       :response response})))))

;; # Concerning creating ASGs for an application

(defn create-auto-scaling-group
  "If the specified application already has an ASG in the given region and
   environment, create the next one. Otherwise, create a brand-new ASG."
  [{:keys [application environment region] ticket-id :id :as parameters}]
  (let [asg-name (str application "-" environment)]
    (if-let [asg (last-auto-scaling-group region asg-name)]
      (let [old-asg-name (:autoScalingGroupName asg)]
        (store/add-to-deployment-parameters
         ticket-id
         {:oldAutoScalingGroupName old-asg-name})
        (create-next-asg parameters))
      (create-new-asg parameters))))

;; Pre-hook attached to `create-auto-scaling-group` which logs the parameters.
(with-pre-hook! #'create-auto-scaling-group
  (fn [parameters]
    (log/debug "Creating ASG with parameters" parameters)))
