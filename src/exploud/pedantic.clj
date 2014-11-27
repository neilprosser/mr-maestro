(ns exploud.pedantic
  "## Integration with Pedantic"
  (:require [cemerick.url :refer [url]]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [exploud.http :as http]))

(def get-timeout
  "The number of milliseconds we'll wait for get requests."
  15000)

(def post-timeout
  "The number of milliseconds we'll wait for post requests."
  180000)

(def pedantic-url
  "We only need the URL for Pedantic."
  (url (env :pedantic-baseurl)))

(defn create-application-url
  "The URL to create a new app in Pedantic."
  [app-name]
  (str (url pedantic-url "1.x" "apps" app-name)))

(defn configuration-url
  "The URL to obtain the configuration for an application in an environment."
  [environment app-name]
  (str (url pedantic-url "1.x" "envs" environment "apps" app-name)))

(defn apply-url
  "The URL we use to apply configuration."
  [environment app-name]
  (str (url pedantic-url "1.x" "envs" environment "apps" app-name "apply")))

(defn envs-url
  "The URL to find environments in Pedantic."
  []
  (str (url pedantic-url "1.x" "envs")))

(defn apply-config
  "Tells Pedantic to apply a config to all environments."
  [app-name]
  (let [{:keys [body status] :as response} (http/simple-get (envs-url) {:socket-timeout get-timeout})]
    (if (= 200 status)
      (let [envs (:environments (json/parse-string body true))
            responses (pmap #(http/simple-get (apply-url % app-name) {:socket-timeout post-timeout}) envs)]
        (map (fn [{:keys [status] :as r}]
               (when-not (= 200 status)
                 (throw (ex-info (str "Unexpected status while applying Pedantic config: " status) {:type ::unexpected-response :response r})))
               r)
             responses))
      (throw (ex-info (str "Unexpected status while getting Pedantic environments: " status) {:type ::unexpected-response :response response})))))

(defn configuration
  "Grab the configuration for an application in the specified environment."
  [environment app-name]
  (let [{:keys [body status] :as response} (http/simple-get (configuration-url environment app-name) {:socket-timeout get-timeout})]
    (cond (= 200 status) (json/parse-string body true)
          (= 403 status) nil
          (= 404 status) nil
          :else (throw (ex-info (str "Unexpected status while getting configuration for " app-name " in " environment ".") {:type ::unexpected-response :response response})))))

(defn upsert-application
  "Insert an application into Pedantic if it doesn't exist, or update it if it does."
  [app-name]
  (let [{:keys [body status] :as response} (http/simple-post (create-application-url app-name) {:socket-timeout post-timeout})]
    (if (or (= status 200) (= status 201))
      (json/parse-string body true)
      (throw (ex-info (str "Unexpected status while creating application in Pedantic: " status) {:type ::unexpected-response :response response})))))
