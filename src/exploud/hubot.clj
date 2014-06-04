(ns exploud.hubot
  (:require [cemerick.url :refer [url]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [exploud.http :as http]))

(def ^:private general
  "503594")

(def ^:private timeout
  5000)

(def ^:private hubot-url
  (url (env :hubot-url)))

(def speak-url
  (str (url hubot-url "hubot" "say")))

(defn speak
  [room message]
  (let [content (json/generate-string {:room room :message message})
        post-body {:content-type :json :body content :socket-timeout timeout}]
    (try
      (http/simple-post speak-url post-body)
      nil
      (catch Exception e
        (log/warn e "Failed while making Hubot talk")))))

(defn speak-about-deployment-start
  [{:keys [application environment message rollback silent user] :as deployment}]
  (when-not silent
    (let [image-id (get-in deployment [:new-state :image-details :id])
          version (get-in deployment [:new-state :image-details :version])]
      (if-not rollback
        (speak general (format "%s is deploying %s v%s (%s) to %s. %s"
                               user application version image-id environment message))
        (speak general (format "%s is rolling back %s to v%s (%s) in %s. %s"
                               user application version image-id environment message))))))

(defn speak-about-deployment-undo
  [{:keys [application environment undo-message undo-silent undo-user] :as deployment}]
  (when-not undo-silent
    (let [new-image-id (get-in deployment [:new-state :image-details :id])
          new-version (get-in deployment [:new-state :image-details :version])
          old-image-id (get-in deployment [:previous-state :image-details :id])
          old-version (get-in deployment [:previous-state :image-details :version])]
      (if (and old-image-id
               old-version)
        (speak general (format "%s is undoing deployment of %s v%s (%s) in %s and replacing it with v%s (%s). %s" undo-user application new-version new-image-id environment old-version old-image-id undo-message))
        (speak general (format "%s is undoing deployment of %s v%s (%s) in %s. %s" undo-user application new-version new-image-id environment undo-message))))))
