(ns maestro.policies
  (:require [amazonica.aws.autoscaling :as autoscaling]
            [bouncer
             [core :as b]
             [validators :as v]]
            [maestro
             [aws :as aws]
             [log :as log]
             [util :as util]]))

(def adjustment-types
  #{"ChangeInCapacity" "ExactCapacity" "PercentChangeInCapacity"})

(v/defvalidator valid-adjustment-type?
  {:default-message-format "%s must be a valid adjustment-type"}
  [input]
  (if input
    (contains? adjustment-types input)
    true))

(def policy-validators
  {:adjustment-type valid-adjustment-type?})

(defn create-policy
  [environment region {:keys [policy-name] :as policy}]
  (log/write (format "Creating scaling policy %s." (:policy-name policy)))
  (let [result (apply autoscaling/put-scaling-policy (cons (aws/config environment region) (util/to-params policy)))]
    [policy-name (:policy-arn result)]))

(defn create-policies
  [environment region policies]
  (into {} (map (partial create-policy environment region) policies)))

(defn policies-for-auto-scaling-group
  [environment region auto-scaling-group-name]
  (:scaling-policies (autoscaling/describe-policies (aws/config environment region) :auto-scaling-group-name auto-scaling-group-name)))
