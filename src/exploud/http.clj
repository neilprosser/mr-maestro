(ns exploud.http
  (:require [clj-http.client :as http]))

(defn default-params [& [overrides]]
  (merge {:throw-exceptions false
          :conn-timeout 1000
          :socket-timeout 5000}
         overrides))

(defn simple-get [url]
  (http/get url (default-params)))

(defn simple-post [url params]
  (http/post url (default-params params)))
