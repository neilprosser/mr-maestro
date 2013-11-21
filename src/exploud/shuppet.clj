(ns exploud.shuppet
  "## integration with Shuppet"
  (:require [cheshire.core :as json]
            [environ.core :refer [env]]
            [exploud.http :as http]))

(def shuppet-url
  "We only need the URL for the 'poke' Shuppet."
  (env :service-shuppet-url))

(defn- get-application-url
  "Shuppet URL to get the details of an application."
  [environment app-name]
  (str shuppet-url "/1.x/envs/" environment "/apps/" app-name))

(defn create-application-url
  "URL to create a new app in Shuppet."
  [app-name]
  (str shuppet-url "/1.x/apps/" app-name))

(defn get-application
  "Fetch the app with the given name from Shuppet."
  [app-name]
  (let [{:keys [body status]}
        (http/simple-get (get-application-url "poke" app-name))]
    (when (= status 200)
      (json/parse-string body true))))

(defn create-application
  "Create a new app with the given name in Shuppet."
  [app-name]
  (let [{:keys [body status] :as response}
        (http/simple-post (create-application-url app-name))]
    (if (= status 201)
      (json/parse-string body true)
      (throw (ex-info (str "Unexpected status while creating application in Shuppet: " status)
                      {:type ::unexpected-response
                       :response response})))))

(defn upsert-application
  "Create the app with the given name in Shuppet or, if it already exists, just fetch it."
  [app-name]
  (if-let [application (get-application app-name)]
    application
    (create-application app-name)))
