(ns exploud.messages.notification
  (:require [exploud
             [notification :as n]
             [responses :refer :all]]))

(defn send-completion-notification
  [{:keys [parameters]}]
  (try
    (n/send-completion-message parameters)
    (success parameters)
    (catch Exception e
      (error-with e))))
