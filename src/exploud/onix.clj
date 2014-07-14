(ns exploud.onix
  "## Integration with Onix"
  (:require [cheshire.core :as json]
            [cemerick.url :refer [url]]
            [environ.core :refer [env]]
            [exploud.http :as http]))

(def onix-url
  "The URL where Onix is running."
  (url (env :service-onix-url)))

(defn applications-url
  "The URL where we can get information about the applications Onix knows
   about."
  []
  (str (url onix-url "1.x" "applications")))

(defn application-url
  "The URL where we can get information about a specific application."
  [application-name]
  (str (url onix-url "1.x" "applications" application-name)))

(defn environments-url
  "The URL where we can get information about the environments Onix knows about."
  []
  (str (url onix-url "1.x" "environments")))

(defn environment-url
  "The URL where we can get information about a specific environment."
  [environment-name]
  (str (url onix-url "1.x" "environments" environment-name)))

(defn property-url
  "The URL where we can get information about the property of an application."
  [application-name property-name]
  (str (url onix-url "1.x" "applications" application-name property-name)))

(defn create-application
  "Creates an applcation in Onix and returns the application. If the
   application is already present, this method will fail."
  [application-name]
  (let [body (json/generate-string {:name application-name})
        {:keys [body status] :as response} (http/simple-post (applications-url) {:content-type :json :body body})]
    (if (= status 201)
      (json/parse-string body true)
      (throw (ex-info "Unexpected status while creating application" {:type ::unexpected-response :response response})))))

(defn add-property
  "Add the given property to the application."
  [application-name property-name property-value]
  (let [body (json/generate-string {:value property-value})
        {:keys [status] :as response} (http/simple-put (property-url application-name (name property-name)) {:content-type :json :body body})]
    (when-not (= status 201)
      (throw (ex-info "Unexpected status while adding property" {:type ::unexpected-response :response response})))))

(defn application
  "Gets a particular application. Returns `nil` if the application doesn't
   exist."
  [application-name]
  (let [{:keys [body status]} (http/simple-get (application-url application-name))]
    (if (= status 200)
      (:metadata (json/parse-string body true)))))

(defn applications
  "Gets all applications Onix knows about."
  []
  (let [{:keys [body status]} (http/simple-get (applications-url))]
    (if (= status 200)
      {:names (:applications (json/parse-string body true))})))

(defn upsert-application
  "Creates an application and returns it if it doesn't exist or just returns it
   if it is already there."
  [application-name]
  (if-let [application (application application-name)]
    application
    (create-application application-name)))

(defn environment
  "Gets a particular environment. Returns `nil` if the environment doesn't exist"
  [environment-name]
  (let [{:keys [body status]} (http/simple-get (environment-url (name environment-name)))]
    (when (= status 200)
      (:metadata (json/parse-string body true)))))

(defn environments
  "Gets all environments Onix knows about."
  []
  (let [{:keys [body status]} (http/simple-get (environments-url))]
    (when (= status 200)
      (apply sorted-set (:environments (json/parse-string body true))))))
