(ns exploud.http
  (:require [clj-http.client :as http]
            [dire.core :refer [with-handler!]]))

(defn default-params [& [overrides]]
  (merge {:throw-exceptions false
          :conn-timeout 5000
          :socket-timeout 15000}
         overrides))

(defn simple-get [url & [params]]
  (http/get url (default-params params)))

(defn simple-post [url & [params]]
  (http/post url (default-params params)))

(with-handler! #'simple-get
  java.net.ConnectException
  (fn [e url]
    (throw (ex-info (.getMessage e) {:type ::connection-refused
                                     :class :http}))))

(with-handler! #'simple-get
  org.apache.http.conn.ConnectTimeoutException
  (fn [e url]
    (throw (ex-info (.getMessage e) {:type ::connect-timeout
                                     :class :http}))))

(with-handler! #'simple-get
  java.net.SocketTimeoutException
  (fn [e url]
    (throw (ex-info (.getMessage e) {:type ::socket-timeout
                                     :class :http}))))

(with-handler! #'simple-get
  java.net.UnknownHostException
  (fn [e url]
    (throw (ex-info (.getMessage e) {:type ::unknown-host
                                     :class :http}))))

(comment (simple-get "http://asgard.brislabs.com:8090"))
