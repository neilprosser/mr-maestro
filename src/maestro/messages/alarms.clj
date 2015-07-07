(ns maestro.messages.alarms
  (:require [amazonica.aws.cloudwatch :as cw]
            [clojure.string :as str]
            [maestro
             [alarms :as alarms]
             [aws :as aws]
             [log :as log]
             [responses :refer [error-with success]]
             [util :as util]]))

(defn swap-policy-arn
  [scaling-policy-arns action-arn]
  (if (map? action-arn)
    (let [{:keys [policy]} action-arn]
      (get scaling-policy-arns policy))
    action-arn))

(defn swap-policy-arns
  [scaling-policy-arns action-arns]
  (when action-arns
    (vec (map (partial swap-policy-arn scaling-policy-arns) action-arns))))

(defn ensure-action-arn
  [scaling-policy-arns {:keys [alarm-actions insufficient-data-actions ok-actions] :as alarm}]
  (-> alarm
      (assoc :alarm-actions (swap-policy-arns scaling-policy-arns alarm-actions))
      (assoc :insufficient-data-actions (swap-policy-arns scaling-policy-arns insufficient-data-actions))
      (assoc :ok-actions (swap-policy-arns scaling-policy-arns ok-actions))
      util/remove-nil-values))

(defn ensure-action-arns
  [{:keys [cloudwatch-alarms scaling-policy-arns]}]
  (when (seq cloudwatch-alarms)
    (vec (map (partial ensure-action-arn scaling-policy-arns) cloudwatch-alarms))))

(defn populate-action-arns
  [{:keys [parameters]}]
  (let [state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [cloudwatch-alarms]} state
        updated-alarms (ensure-action-arns state)]
    (if updated-alarms
      (success (assoc-in parameters [state-key :cloudwatch-alarms] updated-alarms))
      (success parameters))))

(defn create-cloudwatch-alarms
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name cloudwatch-alarms]} state]
    (try
      (if (seq cloudwatch-alarms)
        (let [existing-alarms (alarms/alarms-for-auto-scaling-group environment region auto-scaling-group-name)
              existing-alarm-names (into (hash-set) (map :alarm-name existing-alarms))]
          (doseq [{:keys [alarm-name] :as alarm} cloudwatch-alarms]
            (if-not (contains? existing-alarm-names alarm-name)
              (do
                (log/write (format "Creating CloudWatch alarm %s" alarm-name))
                (apply cw/put-metric-alarm (cons (aws/config environment region) (util/to-params alarm))))
              (log/write (format "CloudWatch alarm %s already exists" alarm-name)))))
        (log/write "No CloudWatch alarms to add"))
      (success parameters)
      (catch Exception e
        (error-with e)))))

(defn remove-old-cloudwatch-alarms
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/previous-state-key parameters)
        state (state-key parameters)]
    (if-let [old-auto-scaling-group-name (:auto-scaling-group-name state)]
      (try
        (let [old-alarms (alarms/alarms-for-auto-scaling-group environment region old-auto-scaling-group-name)
              old-alarm-names (map :alarm-name old-alarms)]
          (when-not (zero? (count old-alarm-names))
            (log/write (format "Deleting existing CloudWatch alarms [%s]" (str/join ", " old-alarm-names)))
            (cw/delete-alarms (aws/config environment region)
                              :alarm-names (vec old-alarm-names))))
        (success parameters)
        (catch Exception e
          (error-with e)))
      (success parameters))))
