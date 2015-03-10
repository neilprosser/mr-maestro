(ns maestro.hubot
  (:require [cemerick.url :refer [url]]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [maestro
             [environments :as environments]
             [http :as http]]))

(def ^:private deployments-room
  (env :hubot-deployments-room))

(def ^:private timeout
  5000)

(def ^:private hubot-url
  (url (env :hubot-url)))

(def ^:private ui-base-url
  (str/replace (env :ui-baseurl "http://maestro") #"/*$" ""))

(def speak-url
  (str (url hubot-url "hubot" "say-many")))

(defn speak
  [rooms message]
  (when (seq rooms)
    (let [content (json/generate-string {:rooms rooms :message message})
          post-body {:content-type :json :body content :socket-timeout timeout}]
      (try
        (http/simple-post speak-url post-body)
        nil
        (catch Exception e
          (log/warn e "Failed while making Hubot talk"))))))

(defn- ui-url
  [id]
  (str ui-base-url "/#/deployments/" id))

(defn- bold
  [s]
  (str "*" s "*"))

(defn deployment-start-message
  [{:keys [application environment id message user] :as deployment} include-message?]
  (let [version (get-in deployment [:new-state :image-details :version])
        basic (str (bold user) " is deploying " (bold application) " v" version " to " (bold environment))]
    (if include-message?
      (str basic "\n>>> " message "\n" (ui-url id))
      basic)))

(defn rollback-start-message
  [{:keys [application environment id message user] :as deployment} include-message?]
  (let [version (get-in deployment [:new-state :image-details :version])
        basic (str (bold user) " is rolling back " (bold application) " to v" version " in " (bold environment))]
    (if include-message?
      (str basic "\n>>> " message "\n" (ui-url id))
      basic)))

(defn undo-start-message
  [{:keys [application environment undo-message undo-user] :as deployment} include-message?]
  (let [new-version (get-in deployment [:new-state :image-details :version])
        old-version (get-in deployment [:previous-state :image-details :version])
        basic (str (bold undo-user) " is undoing deployment of " (bold application) " v" new-version " in " (bold environment) (when old-version (str " and replacing it with v" old-version)))]
    (if include-message?
      (str basic "\n> " undo-message)
      basic)))

(defn deployment-completion-message
  [{:keys [application environment id message user] :as deployment} include-message?]
  (let [version (get-in deployment [:new-state :image-details :version])
        basic (str (bold user) " has deployed " (bold application) " v" version " to " (bold environment))]
    (if include-message?
      (str basic "\n>>> " message "\n" (ui-url id))
      basic)))

(defn undo-completion-message
  [{:keys [application environment undo-message undo-user] :as deployment} include-message?]
  (let [new-version (get-in deployment [:new-state :image-details :version])
        old-version (get-in deployment [:previous-state :image-details :version])
        basic (str (bold undo-user) " has undone the deployment of " (bold application) " v" new-version " in " (bold environment) (when old-version (str " and replaced it with v" old-version)))]
    (if include-message?
      (str basic "\n> " undo-message)
      basic)))

(defn notify?
  [environment silent]
  (or (environments/should-notify? environment)
      (not silent)))

(defn speak-about-deployment-start
  [{:keys [environment rollback silent] :as deployment}]
  (when (notify? environment silent)
    (let [rooms (get-in deployment [:new-state :onix :rooms])]
      (if-not rollback
        (speak rooms (deployment-start-message deployment true))
        (speak rooms (rollback-start-message deployment true))))))

(defn speak-about-undo-start
  [{:keys [environment undo-silent] :as deployment}]
  (when (notify? environment undo-silent)
    (let [rooms (get-in deployment [:new-state :onix :rooms])]
      (speak rooms (undo-start-message deployment true)))))

(defn speak-about-deployment-completion
  [{:keys [environment silent] :as deployment}]
  (when (notify? environment silent)
    (speak [deployments-room] (deployment-completion-message deployment true))
    (let [rooms (get-in deployment [:new-state :onix :rooms])]
      (speak rooms (deployment-completion-message deployment false)))))

(defn speak-about-undo-completion
  [{:keys [environment undo-silent] :as deployment}]
  (when (notify? environment undo-silent)
    (speak [deployments-room] (undo-completion-message deployment true))
    (let [rooms (get-in deployment [:new-state :onix :rooms])]
      (speak rooms (undo-completion-message deployment false)))))
