(ns maestro.messages.policies
  (:require [maestro
             [policies :as policies]
             [responses :refer :all]
             [util :as util]]))

(defn create-scaling-policies
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [scaling-policies]} state]
    (try
      (let [policy-arns (policies/create-policies environment region scaling-policies)]
        (success (assoc-in parameters [state-key :scaling-policy-arns] policy-arns)))
      (catch Exception e
        (error-with e)))))
