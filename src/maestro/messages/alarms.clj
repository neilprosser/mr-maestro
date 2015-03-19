(ns maestro.messages.alarms
  (:require [amazonica.aws.cloudwatch :as cw]
            [maestro
             [alarms :as alarms]
             [aws :as aws]
             [log :as log]
             [responses :refer [error-with success]]
             [util :as util]]))

(defn create-cloudwatch-alarms
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name cloudwatch-alarms]} state]
    (try
      (if (seq cloudwatch-alarms)
        (let [existing-alarms (:metric-alarms (cw/describe-alarms (aws/config environment region)
                                                                  :alarm-name-prefix (str auto-scaling-group-name "-")))
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
        (let [old-alarms (:metric-alarms (cw/describe-alarms (aws/config environment region)
                                                             :alarm-name-prefix (str old-auto-scaling-group-name "-")))
              old-alarm-names (map :alarm-name old-alarms)]
          (when-not (zero? (count old-alarm-names))
            (log/write (format "Deleting existing CloudWatch alarms %s" old-alarm-names))
            (cw/delete-alarms (aws/config environment region)
                              :alarm-names (vec old-alarm-names))))
        (success parameters)
        (catch Exception e
          (error-with e)))
      (success parameters))))
