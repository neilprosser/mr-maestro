(ns exploud.healthchecks
  "## Involved in the business of checking health"
  (:require [clj-time.core :as time]
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

(defn instance-healthy?
  "`true` if the healthcheck returns `200`, otherwise `false."
  [instance port healthcheck-path]
  (try+
   (let [ip (instance-ip instance)
         healthcheck-path (util/strip-first-forward-slash healthcheck-path)
         url (str "http://" ip ":" port "/" healthcheck-path)
         {:keys [status]} (http/simple-get url {:socket-timeout 2000})]
     (= status 200))
   (catch ExceptionInfo _
     false)))

;; # Concerning the checking of ASGs

(defn asg-healthy?
  "`true` if `min-instances` instances in `asg-name` are giving a `200` response
   on their heathchecks, otherwise `false."
  [region asg-name min-instances port healthcheck-path]
  (when-let [instances (asgard/instances-in-asg region asg-name)]
    (let [check (fn [i] (instance-healthy? i port healthcheck-path))]
      (= min-instances (count (filter true? (map check instances)))))))

;; We're going to need this guy in a minute.
(declare check-asg-health)

(defn schedule-asg-check
  "Schedules an ASG healthcheck, which will use `asg-healthy?` to determine
   health."
  [region asg-name min-instances port healthcheck-path deployment-id task
   completed-fn timed-out-fn polls & [delay]]
  (let [f #(check-asg-health region asg-name min-instances port healthcheck-path
                             deployment-id task completed-fn timed-out-fn
                             polls)]
    (at-at/after (or delay 5000) f tasks/pool
                 :desc (str "asg-healthcheck-" deployment-id))))

(defn check-asg-health
  "If `asg-name` is healthy call `completed-fn` otherwise reschedule until
   `seconds` has elapsed. If we've timed out call `timed-out-fn`."
  [region asg-name min-instances port healthcheck-path deployment-id task
   completed-fn timed-out-fn polls]
  (try
    (let [healthy? (asg-healthy? region asg-name min-instances port
                                 healthcheck-path)
          message (str "Checking healthcheck on port " port " and path /"
                       (util/strip-first-forward-slash healthcheck-path) ".")
          updated-log (conj (:log task) {:date (time/now)
                                         :message message})
          updated-task (assoc task :log updated-log :status "running")]
      (store/store-task deployment-id updated-task)
      (if healthy?
        (completed-fn deployment-id updated-task)
        (cond (zero? polls)
              (timed-out-fn deployment-id updated-task)
              :else
              (schedule-asg-check region asg-name min-instances port
                                  healthcheck-path deployment-id updated-task
                                  completed-fn timed-out-fn (dec polls)))))
    (catch Exception e
      (do
        (log/error "Caught exception" e (map str (.getStackTrace e)))
        (throw e)))))

(defn wait-until-asg-healthy
  "Polls every 5 seconds until `asg-healthy?` comes back `true` or until we've
   done `poll-count` checks."
  [region asg-name min-instances port healthcheck-path deployment-id task
   completed-fn timed-out-fn]
  (schedule-asg-check region asg-name min-instances port healthcheck-path
                      deployment-id task completed-fn timed-out-fn poll-count
                      100))

;; Pre-hook attached to `wait-until-asg-healthy` to log parameters.
(with-pre-hook! #'wait-until-asg-healthy
  (fn [region asg-name min-instances port healthcheck-path deployment-id task
      completed-fn timed-out-fn]
    (log/debug "Waiting until ASG healthy with parameters"
               {:region region
                :asg-name asg-name
                :min-instances min-instances
                :port port
                :healthcheck-path healthcheck-path
                :deployment-id deployment-id
                :task task
                :completed-fn completed-fn
                :timed-out-fn timed-out-fn})))

;; # Concerning the checking of ELBs

(defn elb-healthy?
  "`true` if all instances in `asg-name` in `elb-name` have a `:state` of
   `InService`."
  [region elb-name asg-name]
  (let [elb (asgard/load-balancer region elb-name)
        asg-filter (fn [i] (= (:autoScalingGroupName i) asg-name))
        instances (filter asg-filter (:instanceStates elb))]
    (every? (fn [i] (= "InService" (:state i))) instances)))

(declare check-elb-health)

(defn schedule-elb-check
  "Schedules an ELB healthcheck, which will use `elb-healthy?` to determine
   health of all `elb-names`."
  [region elb-names asg-name deployment-id task completed-fn timed-out-fn polls
   & [delay]]
  (let [f #(check-elb-health region elb-names asg-name deployment-id task
                             completed-fn timed-out-fn polls)]
    (at-at/after (or delay 5000) f tasks/pool
                 :desc (str "elb-healthcheck-" deployment-id))))

(defn check-elb-health
  "This check will look at the members of each ELB which belong to the ASG. If
   they're all showing a `:state` of `InService` it's all good."
  [region elb-names asg-name deployment-id task completed-fn timed-out-fn polls]
  (try
    (let [elb-names (util/list-from elb-names)]
      (if-let [elb-name (first elb-names)]
        (let [healthy? (elb-healthy? region elb-name asg-name)
              message (str "Checking ELB (" elb-name ") health.")
              updated-log (conj (:log task) {:date (time/now)
                                             :message message})
              updated-task (assoc task :log updated-log :status "running")]
          (store/store-task deployment-id updated-task)
          (if healthy?
            (schedule-elb-check region (rest elb-names) asg-name deployment-id
                                updated-task completed-fn timed-out-fn
                                (dec polls))
            (cond
             (zero? polls)
             (timed-out-fn deployment-id updated-task)
             :else
             (schedule-elb-check region elb-names asg-name deployment-id
                                 updated-task completed-fn timed-out-fn
                                 (dec polls)))))
        (completed-fn deployment-id task)))
    (catch Exception e
      (do
        (log/error "Caught exception" e (map str (.getStackTrace e)))
        (throw e)))))

(defn wait-until-elb-healthy
  "Polls every 5 seconds until `elb-healthy?` comes back `true` for all ELBs or
   until we've done `poll-count` checks."
  [region elb-names asg-name deployment-id task completed-fn timed-out-fn]
  (schedule-elb-check region elb-names asg-name deployment-id task completed-fn
                      timed-out-fn poll-count))

;; Pre-hook attached to `wait-until-elb-healthy` to log parameters.
(with-pre-hook! #'wait-until-elb-healthy
  (fn [region elb-names asg-name deployment-id task completed-fn timed-out-fn]
    (log/debug "Waiting until ELB healthy with parameters"
               {:region region
                :elb-names elb-names
                :asg-name asg-name
                :deployment-id deployment-id
                :task task
                :completed-fn completed-fn
                :timed-out-fn timed-out-fn})))
