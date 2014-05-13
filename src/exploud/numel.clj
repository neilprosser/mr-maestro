(ns exploud.numel
  (:require [cemerick.url :refer [url]]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [exploud.http :as http]))

(def timeout
  "The number of milliseconds we'll wait for a response."
  10000)

(def poke-numel-url
  "The base URL for Numel in poke"
  (url (env :service-numel-poke-url)))

(def prod-numel-url
  "The base URL for Numel in poke"
  (url (env :service-numel-prod-url)))

(defn application-registrations-url
  [environment application]
  (if (= "prod" (name environment))
    (str (url prod-numel-url "1.x" "registration" application))
    (str (url poke-numel-url "1.x" "registration" application))))

(defn application-registrations
  [environment application]
  (let [url (application-registrations-url environment application)
        {:keys [body status] :as response} (http/simple-get url {:socket-timeout timeout})]
    (if (= 200 status)
      (json/parse-string body true)
      (throw (ex-info "Unexpected response" {:type ::unexpected-response :response response})))))
