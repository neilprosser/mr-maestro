(ns exploud.onix
  (:require [cheshire.core :as json]
            [environ.core :refer [env]]
            [exploud.http :as http]))

(def onix-url
  (env :service-onix-url))

(defn applications-url []
  (str onix-url "/1.x/applications"))

(defn application-url [application-name]
  (str onix-url "/1.x/applications/" application-name))

(defn create-application [application-name]
  (let [body (json/generate-string {:name application-name})
        {:keys [body status] :as response} (http/simple-post (applications-url) {:content-type :json :body body})]
    (if (= status 201)
      (json/parse-string body true)
      (throw (ex-info "Unexpected status while creating application" {:type ::unexpected-response
                          :response response})))))

(defn application [application-name]
  (let [{ :keys [body status]} (http/simple-get (application-url application-name))]
    (if (= status 200)
      (json/parse-string body true))))

(defn upsert-application [application-name]
  (if-let [application (application application-name)]
    application
    (create-application application-name)))
