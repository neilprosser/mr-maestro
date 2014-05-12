(ns exploud.validators
  (:require [bouncer
             [core :as b]
             [validators :as v]]
            [clj-time.format :as fmt]
            [exploud.util :as util]))

(def healthcheck-types
  #{"EC2" "ELB"})

(def instance-types
  #{"c1.medium"
    "c1.xlarge"
    "c3.2xlarge"
    "c3.4xlarge"
    "c3.8xlarge"
    "c3.large"
    "c3.xlarge"
    "cc2.8xlarge"
    "cg1.4xlarge"
    "cr1.8xlarge"
    "g2.2xlarge"
    "hi1.4xlarge"
    "hs1.8xlarge"
    "i2.2xlarge"
    "i2.4xlarge"
    "i2.8xlarge"
    "i2.xlarge"
    "m1.large"
    "m1.medium"
    "m1.small"
    "m1.xlarge"
    "m2.2xlarge"
    "m2.4xlarge"
    "m2.xlarge"
    "m3.2xlarge"
    "m3.large"
    "m3.medium"
    "m3.xlarge"
    "r3.2xlarge"
    "r3.4xlarge"
    "r3.8xlarge"
    "r3.large"
    "r3.xlarge"
    "t1.micro"})

(def availability-zones
  #{"a" "b" "c"})

(def subnet-purposes
  #{"internal" "mgmt" "publiceip" "publicnat"})

(def termination-policies
  #{"ClosestToNextInstanceHour" "Default" "NewestInstance" "OldestInstance" "OldestLaunchConfiguration"})

(defn zero-or-more?
  "Whether a given input is zero or more."
  [input]
  (if input
    (if-let [number (util/string->int input)]
      (or (zero? number) (v/positive number))
      false)
    true))

(defn positive?
  "Whether a given input is a positive number."
  [input]
  (if input
    (if-let [number (util/string->int input)]
      (v/positive number)
      false)
    true))

(defn valid-date?
  "Whether the given input is a valid date."
  [input]
  (if input
    (try
      (fmt/parse input)
      (catch Exception _
        false))
    true))

(defn valid-boolean?
  "Whether the given input is a valid boolean."
  [input]
  (if input
    (or (= (str input) "true")
        (= (str input) "false"))
    true))

(defn valid-healthcheck-type?
  "Whether the given input is a valid healthcheck type"
  [input]
  (if input
    (contains? healthcheck-types input)
    true))

(defn valid-instance-type?
  "Whether the given input is a valid instance type"
  [input]
  (if input
    (contains? instance-types input)
    true))

(defn valid-availability-zone?
  "Whether the given input is a valid availability zone"
  [input]
  (if input
    (contains? availability-zones input)
    true))

(defn valid-availability-zones?
  "Whether the given input is either a single, valid availability zone or a collection of valid availability zones"
  [input]
  (if input
    (if (coll? input)
      (every? valid-availability-zone? input)
      (valid-availability-zone? input))
    true))

(defn valid-subnet-purpose?
  "Whether the given input is a valid subnet purpose"
  [input]
  (if input
    (contains? subnet-purposes input)
    true))

(defn valid-termination-policy?
  "Whether the given input is a valid termination policy"
  [input]
  (if input
    (contains? termination-policies input)
    true))

(def query-param-validators
  "The validators we should `apply` to validate query parameters."
  [:from [[zero-or-more? :message "from must be zero or more"]]
   :full [[valid-boolean? :message "full must be 'true' or 'false'"]]
   :size [[positive? :message "size must be positive"]]
   :start-from [[valid-date? :message "start-from must be a valid date"]]
   :start-to [[valid-date? :message "start-to must be a valid date"]]])

(def log-param-validators
  "The validators we should `apply` to validate deployment log parameters."
  [:since [[valid-date? :message "since must be a valid date"]]])

(def deployment-param-validators
  "The validators we should `apply` to deployment parameters."
  [:default-cooldown [[positive? :message "default cooldown must be positive"]]
   :desired-capacity [[positive? :message "desired capacity must be positive"]]
   :health-check-grace-period [[positive? :message "healthcheck grace period must be positive"]]
   :health-check-type [[valid-healthcheck-type? :message "healthcheck type must be either 'EC2' or 'ELB'"]]
   :instance-healthy-attempts [[positive? :message "number of instance healthcheck attempts must be positive"]]
   :instance-type [[valid-instance-type? :message "instance type must be a known instance type"]]
   :load-balancer-healthy-attempts [[positive? :message "number of load balancer healthcheck attempts must be positive"]]
   :max [[positive? :message "maximum number of instances must be positive"]]
   :min [[zero-or-more? :message "minimum number of instances must be zero or more"]]
   :pause-after-instances-healthy [[valid-boolean? :message "pause after instances healthy must be 'true' or 'false'"]]
   :pause-after-load-balancers-healthy [[valid-boolean? :message "pause after load balancers healthy must be 'true' or 'false'"]]
   :selected-zones [[valid-availability-zones? :message "selected zones must be valid availability zones"]]
   :subnet-purpose [[valid-subnet-purpose? :message "subnet purpose must be valid"]]
   :termination-policy [[valid-termination-policy? :message "termination policy must be valid"]]])
