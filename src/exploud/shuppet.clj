(ns exploud.shuppet
  "## integration with Shuppet"
  (:require [cheshire.core :as json]
            [environ.core :refer [env]]
            [exploud.http :as http]))

(def shuppet-url
  "We only need the URL for the 'poke' Shuppet."
  (env :service-shuppet-url))

(defn create-application-url
  "URL to create a new app in Shuppet."
  [app-name]
  (str shuppet-url "/1.x/apps/" app-name))

(defn upsert-application
  [app-name]
  (let [{:keys [body status] :as response}
        (http/simple-post (create-application-url app-name))]
    (if (or (= status 200) (= status 201))
      (json/parse-string body true)
      (throw (ex-info (str "Unexpected status while creating application in Shuppet: " status)
                      {:type ::unexpected-response
                       :response response})))))
