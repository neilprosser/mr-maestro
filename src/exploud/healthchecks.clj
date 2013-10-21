(ns exploud.healthchecks
  (:require [exploud
             [asgard :as asgard]
             [http :as http]
             [store :as store]
             [util :as util]]
            [overtone.at-at :as at-at]))

(def task-pool
  "A pool which we'll use for checking the health of instances."
  (at-at/mk-pool))

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

(defn strip-first-forward-slash
  "Turns `/this/that` into `this/that`."
  [thing]
  (second (re-find #"^/([^/]+)$" thing)))

(defn instance-healthy?
  "`true` if the healthcheck returns `200`, otherwise `false."
  [instance]
  (let [ip (instance-ip instance)
        port 8080
        healthcheck-path (strip-first-forward-slash "/healthcheck")
        url (str "http://" ip ":" port "/" healthcheck-path)
        {:keys [status]} (http/simple-get url)]
    (= status 200)))

;; # Concerning the checking of ASGs

(defn asg-healthy?
  "`true` if all instances in `asg-name` are giving a `200` response on their
   heathchecks, otherwise `false."
  [region asg-name]
  (when-let [instances (asgard/instances-in-asg region asg-name)]
    (every? true? (map instance-healthy? instances))))

;; We're going to need this guy in a minute.
(declare check-asg-health)

(defn schedule-asg-check
  "Schedules an ASG healthcheck, which will use `asg-healthy?` to determine
   health."
  [region asg-name deployment-id task completed-fn timed-out-fn polls]
  (let [f #(check-asg-health region asg-name deployment-id task
                             completed-fn timed-out-fn polls)]
    (at-at/after 5000 f)))

(defn check-asg-health
  "If `asg-name` is healthy call `completed-fn` otherwise reschedule until
   `seconds` has elapsed. If we've timed out call `timed-out-fn`."
  [region asg-name deployment-id task completed-fn timed-out-fn polls]
  (let [healthy? (asg-healthy? region asg-name)
        updated-log (conj (:log task) {:date (util/now-string)
                                       :message "Polled again."})
        updated-task (assoc task :log updated-log)]
    (store/store-task deployment-id updated-task)
    (if healthy?
      (completed-fn deployment-id updated-task)
      (cond (zero? polls)
            (timed-out-fn deployment-id updated-task)
            :else
            (schedule-asg-check region asg-name deployment-id updated-task
                              completed-fn timed-out-fn (dec polls))))))

(defn wait-until-asg-healthy
  "Polls every 5 seconds until `asg-healthy?` comes back `true` or until we've
   done `poll-count` checks."
  [region asg-name deployment-id task completed-fn timed-out-fn]
  (schedule-asg-check region asg-name deployment-id task
                      completed-fn timed-out-fn poll-count))

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
   health."
  [region elb-name asg-name deployment-id task completed-fn timed-out-fn polls]
  (let [f #(check-elb-health region elb-name asg-name deployment-id task
                             completed-fn timed-out-fn polls)]
    (at-at/after 5000 f)))

(defn check-elb-health
  "This check will look at the members of the ELB which belong to the ASG. If
   they're all showing a `:state` of `InService` it's all good."
  [region elb-name asg-name deployment-id task completed-fn timed-out-fn polls]
  (let [healthy? (elb-healthy? region elb-name asg-name)
        updated-log (conj (:log task) {:date (util/now-string)
                                       :message "Polled again."})
        updated-task (assoc task :log updated-log)]
    (store/store-task deployment-id updated-task)
    (if healthy?
      (completed-fn deployment-id updated-task)
      (cond (zero? polls)
            (timed-out-fn deployment-id updated-task)
            :else
            (schedule-elb-check region elb-name asg-name deployment-id
                                updated-task completed-fn timed-out-fn
                                (dec polls))))))

(defn wait-until-elb-healthy
  "Polls every 5 seconds until `elb-healthy?` comes back `true` or until we've
   done `poll-count` checks."
  [region elb-name asg-name deployment-id task completed-fn timed-out-fn]
  (schedule-elb-check region elb-name asg-name deployment-id task completed-fn
                      timed-out-fn poll-count))
