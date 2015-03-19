(ns maestro.alarms
  (:require [maestro.environments :as environments]))

(def thresholds
  {"t2.micro" 15
   "t2.small" 15
   "t2.medium" 30})

(defn cpu-credit-balance-low-alarm
  [auto-scaling-group-name topic-arn threshold]
  (when (and auto-scaling-group-name topic-arn threshold)
    {:actions-enabled true
     :alarm-actions [topic-arn]
     :alarm-description "This alarm indicates that the credit balance (which governs the speed t2-class instances can run at) is beginning to run out. If these credits run out, that instance will start to throttle its processor."
     :alarm-name (str auto-scaling-group-name "-cpu-credit-balance-low")
     :comparison-operator "LessThanOrEqualToThreshold"
     :dimensions [{:name "AutoScalingGroupName"
                   :value auto-scaling-group-name}]
     :evaluation-periods 1
     :insufficient-data-actions [topic-arn]
     :metric-name "CPUCreditBalance"
     :namespace "AWS/EC2"
     :ok-actions [topic-arn]
     :period 300
     :statistic "Minimum"
     :threshold threshold}))

(defn topic-arn-for
  [environment-name]
  (environments/alert-topic environment-name))

(defn threshold-for
  [instance-type]
  (get thresholds instance-type))

(defn standard-alarms
  [environment region {:keys [auto-scaling-group-name tyranitar] :as state}]
  (let [{:keys [deployment-params]} tyranitar
        {:keys [instance-type]} deployment-params
        topic-arn (topic-arn-for environment)
        threshold (threshold-for instance-type)]
    (remove nil? [(cpu-credit-balance-low-alarm auto-scaling-group-name topic-arn threshold)])))
