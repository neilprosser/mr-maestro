(ns maestro.messages.notification
  (:require [maestro
             [hubot :as hubot]
             [responses :refer :all]]))

(defn send-start-notification
  [{:keys [parameters]}]
  (try
    (let [{:keys [undo]} parameters]
      (if undo
        (hubot/speak-about-undo-start parameters)
        (hubot/speak-about-deployment-start parameters)))
    (success parameters)
    (catch Exception e
      (error-with e))))

(defn send-completion-notification
  [{:keys [parameters]}]
  (try
    (let [{:keys [undo]} parameters]
      (if undo
        (hubot/speak-about-undo-completion parameters)
        (hubot/speak-about-deployment-completion parameters)))
    (success parameters)
    (catch Exception e
      (error-with e))))
