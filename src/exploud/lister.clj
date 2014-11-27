(ns exploud.lister
  "## Integration with Lister"
  (:require [cheshire.core :as json]
            [cemerick.url :refer [url]]
            [environ.core :refer [env]]
            [exploud.http :as http]))

(def lister-url
  "The URL where Lister is running."
  (url (env :lister-baseurl)))

(defn applications-url
  "The URL where we can get information about the applications Lister knows
   about."
  []
  (str (url lister-url "applications")))

(defn application-url
  "The URL where we can get information about a specific application."
  [application-name]
  (str (url lister-url "applications" application-name)))

(defn environments-url
  "The URL where we can get information about the environments Lister knows about."
  []
  (str (url lister-url "environments")))

(defn environment-url
  "The URL where we can get information about a specific environment."
  [environment-name]
  (str (url lister-url "environments" environment-name)))

(defn property-url
  "The URL where we can get information about the property of an application."
  [application-name property-name]
  (str (url lister-url "applications" application-name property-name)))

(defn create-application
  "Creates an applcation in Lister and returns the application. If the
   application is already present, this method will fail."
  [application-name]
  (let [{:keys [body status] :as response} (http/simple-put (application-url application-name))]
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
  "Gets all applications Lister knows about."
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
      (json/parse-string body true))))

(defn environments
  "Gets all environments Lister knows about."
  []
  (let [{:keys [body status]} (http/simple-get (environments-url))]
    (when (= status 200)
      (apply sorted-set (:environments (json/parse-string body true))))))
