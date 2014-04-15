(ns exploud.healthchecks
  "## Involved in the business of checking health"
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [dire.core :refer [with-pre-hook!]]
            [exploud
             [asgard :as asgard]
             [http :as http]
             [store :as store]
             [tasks :as tasks]
             [util :as util]]
            [overtone.at-at :as at-at]
            [slingshot.slingshot :refer [try+]])
  (:import clojure.lang.ExceptionInfo))

(def poll-every-seconds
  "The delay between polling."
  5)

(def poll-count
  "The number of times we should poll."
  60)

(defn instance-ip
  "Grabs the instance IP from `:instance :privateIpAddress`."
  [instance]
  (get-in instance [:instance :privateIpAddress]))

(defn- create-url
  "Create the healthcheck URL we'll call."
  [ip port healthcheck-path]
  (str "http://" ip ":" port "/" healthcheck-path))

(defn ok-response?
  "`true` if the given `url` returns `200` in 2s, otherwise `false`."
  [url]
  (try+
   (let [{:keys [status]} (http/simple-get url {:socket-timeout 2000})]
     (= status 200))
   (catch Exception _
     false)))

(defn check-instance-health
  "Checks that a call to `http://{ip}:{port}{healthcheck-path}` gives an OK response. Returns a map with `:url` and `:success`."
  [ip port healthcheck-path]
  (let [healthcheck-path (util/strip-first-forward-slash healthcheck-path)
        url (create-url ip port healthcheck-path)]
    {:url url
     :successful? (ok-response? url)}))

;; # Concerning the checking of ASGs

(defn determine-asg-health
  "Checks the health of all instances in the ASG and gives back those results and the overall health of the ASG."
  [environment region asg-name min-instances port healthcheck-path]
  (try
    (when-let [instances (asgard/instances-in-asg environment region asg-name)]
      (let [check (fn [i] (check-instance-health (instance-ip i) port healthcheck-path))
            check-results (map check instances)]
        {:healthy? (>= (count (filter :successful? check-results)) min-instances)
         :results check-results}))
    (catch Exception e
      (log/warn e "Failed to determine ASG health, we'll assume it's not yet healthy")
      {:healthy? false})))

;; We're going to need this guy in a minute.
(declare check-asg-health)

(defn schedule-asg-check
  "Schedules an ASG healthcheck, which will use `determine-asg-health` to figure out health."
  [environment region asg-name min-instances port healthcheck-path deployment-id task completed-fn timed-out-fn polls & [delay]]
  (let [f #(check-asg-health environment region asg-name min-instances port healthcheck-path deployment-id task completed-fn timed-out-fn polls)]
    (at-at/after (or delay 5000) f tasks/pool :desc (str "asg-healthcheck-" deployment-id))))

(defn- message-from
  "Creates the log message from an ASG's check results"
  [results]
  (str/join "\n" (map #(format "Healthcheck at %s - %s" (:url %) (:successful? %)) results)))

(defn check-asg-health
  "If `asg-name` is healthy call `completed-fn` otherwise reschedule until `seconds` has elapsed. If we've timed out call `timed-out-fn`."
  [environment region asg-name min-instances port healthcheck-path deployment-id task completed-fn timed-out-fn polls]
  (try
    (let [asg-health (determine-asg-health environment region asg-name min-instances port healthcheck-path)
          healthy? (:healthy? asg-health)
          message (message-from (:results asg-health))
          updated-task (assoc (util/append-to-task-log message task) :status "running")]
      (store/store-task deployment-id updated-task)
      (if healthy?
        (completed-fn deployment-id (assoc updated-task :status "completed"))
        (if (zero? polls)
          (timed-out-fn deployment-id updated-task)
          (schedule-asg-check environment region asg-name min-instances port healthcheck-path deployment-id updated-task completed-fn timed-out-fn (dec polls)))))
    (catch Exception e
      (do
        (log/error e "Caught exception while checking ASG health")
        (throw e)))))

(defn wait-until-asg-healthy
  "Polls every 5 seconds until `determine-asg-health` shows healthy or until we've done `poll-count` checks."
  [environment region asg-name min-instances port healthcheck-path deployment-id task completed-fn timed-out-fn]
  (schedule-asg-check environment region asg-name min-instances port healthcheck-path deployment-id task completed-fn timed-out-fn poll-count 100))

;; Pre-hook attached to `wait-until-asg-healthy` to log parameters.
(with-pre-hook! #'wait-until-asg-healthy
  (fn [environment region asg-name min-instances port healthcheck-path deployment-id task completed-fn timed-out-fn]
    (log/debug "Waiting until ASG healthy with parameters" {:environment environment :region region :asg-name asg-name :min-instances min-instances
                                                            :port port :healthcheck-path healthcheck-path :deployment-id deployment-id :task task
                                                            :completed-fn completed-fn :timed-out-fn timed-out-fn})))

;; # Concerning the checking of ELBs

(defn elb-healthy?
  "`true` if all instances in `asg-name` in `elb-name` have a `:state` of
   `InService`."
  [environment region elb-name asg-name]
  (let [elb (asgard/load-balancer environment region elb-name)
        asg-filter (fn [i] (= (:autoScalingGroupName i) asg-name))
        instances (filter asg-filter (:instanceStates elb))]
    (every? (fn [i] (= "InService" (:state i))) instances)))

(declare check-elb-health)

(defn schedule-elb-check
  "Schedules an ELB healthcheck, which will use `elb-healthy?` to determine
   health of all `elb-names`."
  [environment region elb-names asg-name deployment-id task completed-fn timed-out-fn polls & [delay]]
  (let [f #(check-elb-health environment region elb-names asg-name deployment-id task completed-fn timed-out-fn polls)]
    (at-at/after (or delay 5000) f tasks/pool :desc (str "elb-healthcheck-" deployment-id))))

(defn check-elb-health
  "This check will look at the members of each ELB which belong to the ASG. If
   they're all showing a `:state` of `InService` it's all good."
  [environment region elb-names asg-name deployment-id task completed-fn timed-out-fn polls]
  (try
    (let [elb-names (util/list-from elb-names)]
      (if-let [elb-name (first elb-names)]
        (let [healthy? (elb-healthy? environment region elb-name asg-name)
              message (str "Checking ELB (" elb-name ") health.")
              updated-task (assoc (util/append-to-task-log message task) :status "running")]
          (store/store-task deployment-id updated-task)
          (if healthy?
            (schedule-elb-check environment region (rest elb-names) asg-name deployment-id updated-task completed-fn timed-out-fn (dec polls))
            (if (zero? polls)
             (timed-out-fn deployment-id updated-task)
             (schedule-elb-check environment region elb-names asg-name deployment-id updated-task completed-fn timed-out-fn (dec polls)))))
        (completed-fn deployment-id (assoc task :status "completed"))))
    (catch Exception e
      (do
        (log/error e "Caught exception while checking ELB health")
        (throw e)))))

(defn wait-until-elb-healthy
  "Polls every 5 seconds until `elb-healthy?` comes back `true` for all ELBs or
   until we've done `poll-count` checks."
  [environment region elb-names asg-name deployment-id task completed-fn timed-out-fn]
  (schedule-elb-check environment region elb-names asg-name deployment-id task completed-fn timed-out-fn poll-count))

;; Pre-hook attached to `wait-until-elb-healthy` to log parameters.
(with-pre-hook! #'wait-until-elb-healthy
  (fn [environment region elb-names asg-name deployment-id task completed-fn timed-out-fn]
    (log/debug "Waiting until ELB healthy with parameters" {:environment environment :region region :elb-names elb-names :asg-name asg-name
                                                            :deployment-id deployment-id :task task :completed-fn completed-fn :timed-out-fn timed-out-fn})))
