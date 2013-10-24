(ns exploud.onix
  "## Integration with Onix"
  (:require [cheshire.core :as json]
            [environ.core :refer [env]]
            [exploud.http :as http]))

(def onix-url
  "The URL where Onix is running."
  (env :service-onix-url))

(defn applications-url
  "The URL where we can get information about the applications Onix knows
   about."
  []
  (str onix-url "/1.x/applications"))

(defn application-url
  "The URL where we can get information about a specific application."
  [application-name]
  (str onix-url "/1.x/applications/" application-name))

(defn create-application
  "Creates an applcation in Onix and returns the application. If the
   application is already present, this method will fail."
  [application-name]
  (let [body (json/generate-string {:name application-name})
        {:keys [body status] :as response} (http/simple-post
                                            (applications-url)
                                            {:content-type :json
                                             :body body})]
    (if (= status 201)
      (json/parse-string body true)
      (throw (ex-info "Unexpected status while creating application"
                      {:type ::unexpected-response
                       :response response})))))

(defn application
  "Gets a particular application. Returns `nil` if the application doesn't
   exist."
  [application-name]
  (let [{:keys [body status]} (http/simple-get
                                (application-url application-name))]
    (if (= status 200)
      (json/parse-string body true))))

(defn applications
  "Gets all applications Onix knows about."
  [application-name]
  (let [{:keys [body status]} (http/simple-get
                               (applications-url))]
    (if (= status 200)
      (json/parse-string body true))))

(defn upsert-application
  "Creates an application and returns it if it doesn't exist or just returns it
   if it is already there."
  [application-name]
  (if-let [application (application application-name)]
    application
    (create-application application-name)))
