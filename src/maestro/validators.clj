(ns maestro.validators
  (:require [bouncer
             [core :as b]
             [validators :as v]]
            [clj-time.format :as fmt]
            [clojure
             [set :as set]
             [string :as str]]
            [maestro
             [environments :as environments]
             [util :as util]]
            [ninjakoala.lamarck :as lam]))

(def healthcheck-types
  #{"EC2" "ELB" "EC2+healthy"})

(def para-instance-types
  (conj (set (map :instance-type (filter (fn [i] (contains? (set (:linux-virtualization-types i)) "PV")) lam/instances)))
        "c1.medium"
        "c1.xlarge"
        "hi1.4xlarge"
        "hs1.8xlarge"
        "m1.small"
        "m1.medium"
        "m1.large"
        "m1.xlarge"
        "m2.xlarge"
        "m2.2xlarge"
        "m2.4xlarge"
        "t1.micro"))

(def hvm-instance-types
  (conj (set (map :instance-type (filter (fn [i] (contains? (set (:linux-virtualization-types i)) "HVM")) lam/instances)))
        "cc2.8xlarge"
        "cg1.4xlarge"
        "cr1.8xlarge"
        "hi1.4xlarge"
        "hs1.8xlarge"))

(defn allowed-instances
  [virtualisation-type]
  (cond (= (name virtualisation-type) "para") para-instance-types
        (= (name virtualisation-type) "hvm") hvm-instance-types
        :else (throw (ex-info (format "Unknown virtualisation type '%s'." virtualisation-type) {}))))

(def instance-types
  (set/union para-instance-types hvm-instance-types))

(def availability-zones
  #{"a" "b" "c"})

(def subnet-purposes
  #{"internal" "mgmt" "publiceip" "publicnat"})

(def termination-policies
  #{"ClosestToNextInstanceHour" "Default" "NewestInstance" "OldestInstance" "OldestLaunchConfiguration"})

(v/defvalidator zero-or-more?
  {:default-message-format "%s must be zero or more"}
  [input]
  (if input
    (if-let [number (util/string->int input)]
      (or (zero? number) (v/positive number))
      false)
    true))

(v/defvalidator positive?
  {:default-message-format "%s must be positive"}
  [input]
  (if input
    (if-let [number (util/string->int input)]
      (v/positive number)
      false)
    true))

(v/defvalidator valid-date?
  {:default-message-format "%s must be a valid date"}
  [input]
  (if input
    (try
      (fmt/parse input)
      (catch Exception _
        false))
    true))

(v/defvalidator valid-boolean?
  {:default-message-format "%s must be 'true' or 'false'"}
  [input]
  (if input
    (or (= (str input) "true")
        (= (str input) "false"))
    true))

(v/defvalidator valid-healthcheck-type?
  {:default-message-format "%s must be either 'EC2' or 'ELB'"}
  [input]
  (if input
    (contains? healthcheck-types input)
    true))

(v/defvalidator valid-instance-type?
  {:default-message-format "%s must be a known instance type"}
  [input]
  (if input
    (contains? instance-types input)
    false))

(defn valid-availability-zone?
  [input]
  (if input
    (contains? availability-zones input)
    true))

(v/defvalidator valid-availability-zones?
  {:default-message-format "%s must be valid availability zones"}
  [input]
  (if input
    (if (coll? input)
      (every? valid-availability-zone? input)
      (valid-availability-zone? input))
    true))

(v/defvalidator valid-subnet-purpose?
  {:default-message-format "%s must be a known purpose"}
  [input]
  (if input
    (contains? subnet-purposes input)
    true))

(v/defvalidator valid-termination-policy?
  {:default-message-format "%s must be a valid termination policy"}
  [input]
  (if input
    (contains? termination-policies input)
    true))

(v/defvalidator known-environment?
  {:default-message-format "environment %s is not known"}
  [input]
  (contains? (apply hash-set (keys (environments/environments))) (keyword input)))

(def scheduled-action-validators
  {:cron v/required
   :desired-capacity [v/required zero-or-more?]
   :max [v/required zero-or-more?]
   :min [v/required zero-or-more?]})

(v/defvalidator valid-scheduled-actions?
  {:default-message-format "%s must all be valid scheduled actions"}
  [input]
  (nil? (seq (remove nil? (map (fn [[name description]] (first (b/validate description scheduled-action-validators))) input)))))

(def query-param-validators
  "The validators we should use to validate query parameters."
  {:from zero-or-more?
   :full valid-boolean?
   :size positive?
   :start-from valid-date?
   :start-to valid-date?})

(def log-param-validators
  "The validators we should use to validate deployment log parameters."
  {:since valid-date?})

(def deployment-params-validators
  "The validators we should use to validate Tyranitar deployment parameters."
  {:default-cooldown positive?
   :desired-capacity positive?
   :health-check-grace-period positive?
   :health-check-type valid-healthcheck-type?
   :instance-healthy-attempts positive?
   :instance-type valid-instance-type?
   :load-balancer-healthy-attempts positive?
   :max positive?
   :min zero-or-more?
   :pause-after-instances-healthy valid-boolean?
   :pause-after-load-balancers-healthy valid-boolean?
   :scale-down-after-deployment valid-boolean?
   :scheduled-actions valid-scheduled-actions?
   :selected-zones valid-availability-zones?
   :subnet-purpose valid-subnet-purpose?
   :termination-policy valid-termination-policy?})

(def deployment-request-validators
  "The validators we should use to validate deployment requests."
  {:ami [v/required [v/matches #"^ami-[0-9a-f]{8}$"]]
   :application v/required
   :environment [v/required known-environment?]
   :message v/required})

(def deployment-validators
  "The validators we should use to validate deployment parameters."
  {:application v/required
   :environment [v/required known-environment?]
   :id v/required
   :message v/required
   [:new-state :image-details :id] [v/required [v/matches #"^ami-[0-9a-f]{8}$"]]
   :region v/required
   :user v/required})
