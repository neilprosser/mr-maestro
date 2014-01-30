(ns exploud.web
  "## Setting up our RESTful interface"
  (:require [bouncer.core :as b]
            [cheshire.core :as json]
            [clj-time.format :as fmt]
            [clojure
             [string :refer [split]]
             [walk :refer [keywordize-keys postwalk]]]
            [clojure.tools.logging :as log]
            [compojure
             [core :refer [defroutes context GET PUT POST DELETE]]
             [handler :as handler]
             [route :as route]]
            [environ.core :refer [env]]
            [exploud
             [aws :as aws]
             [deployment :as dep]
             [info :as info]
             [jsonp :refer [wrap-json-with-padding]]
             [pokemon :as pokemon]
             [store :as store]
             [tasks :as tasks]
             [util :as util]
             [validators :as v]]
            [metrics.ring
             [expose :refer [expose-metrics-as-json]]
             [instrument :refer [instrument]]]
            [nokia.ring-utils
             [error :refer [wrap-error-handling error-response]]
             [ignore-trailing-slash :refer [wrap-ignore-trailing-slash]]]
            [overtone.at-at :as at-at]
            [ring.middleware
             [format-response :refer [wrap-json-response]]
             [json-params :refer [wrap-json-params]]
             [params :refer [wrap-params]]
             [keyword-params :refer [wrap-keyword-params]]]
            [ring.util.response :refer [header]]))

(def ^:dynamic *version*
  "The version of our application."
  "none")

(defn set-version!
  "Sets the version of the application."
  [version]
  (alter-var-root #'*version* (fn [_] version)))

(def default-environment
  "The default environment we'll use for queries."
  "poke")

(def default-region
  "The default region we'll deploy to (temporarily)."
  "eu-west-1")

(def default-user
  "The default user we'll say deployments came from (if one isn't provided)."
  "exploud")

(def application-regex
  "The regular expression which we'll use to determine whether an application
   name is valid."
  #"[a-z]+")

(defn response
  "Accepts a body an optionally a content type and status. Returns a response
   object."
  [body & [content-type status]]
  (if body
    {:status (or status 200)
     :headers {"Content-Type" (or content-type
                                  "application/json; charset=utf-8")}
     :body body}
    (if status
      {:status status}
      {:status 404})))

(defn- date-string-to-date
  "If the value is a string and a valid ISO8601 date then turn it into a date. Otherwise leave it alone."
  [v]
  (if (string? v)
    (try (if-let [vd (fmt/parse (fmt/formatters :date-time) v)]
           vd
           v)
         (catch Exception e v))
    v))

(def locked?
  "Atom holding a boolean indicating whether Exploud is locked for actions with side-effects."
  (atom false))

(defn- lock!
  "Lock Exploud, preventing deployments etc."
  []
  (reset! locked? true))

(defn- unlock!
  "Unlock Exploud, allowing deployment etc."
  []
  (reset! locked? false))

(defmacro guarded
  "Replace the given body with a Conflict response if Exploud has been locked."
  [& body]
  `(if @locked?
     (response "Exploud is currently closed for business." "text/plain" 409)
     (do ~@body)))

(def ongoing-deployments
  "An atom containing the set of all in-progress deployments."
  (atom #{}))

(defn- deployment-string
  "Generate a string which combined the environment and application name."
  [application environment]
  (str environment "-" application))

(defn- can-deploy?
  "Determine whether the set of ongoing deployment already contains an entry for the environment and application."
  [application environment]
  (contains? @ongoing-deployments (deployment-string application environment)))

(defn- register-deployment
  "Register a deployment in the set of ongoing deployments."
  [application environment]
  (swap! ongoing-deployments conj (deployment-string application environment)))

(defn- unregister-deployment
  "Removes a deployment from the set of ongoing deployments."
  [application environment]
  (swap! ongoing-deployments disj (deployment-string application environment)))

(defmacro one-at-a-time-please
  "We only allow one action against an application in an environment at a time."
  [application environment & body]
  (if (can-deploy? application environment)
    (try
      (register-deployment application environment)
      (do ~@body)
      (finally
        (unregister-deployment application environment)))
    (response (str "An action for " application " in " environment " is already underway") "text/plain" 409)))

(defroutes routes
  "The RESTful routes we provide."

  (context
   "/1.x" []

   (GET "/ping"
        []
        (response "pong" "text/plain"))

   (GET "/status"
        []
        (response {:name "exploud"
                   :version *version*
                   :success true}))

   (GET "/pokemon"
        []
        (response pokemon/pokemon "text/plain"))

   (GET "/icon"
        []
        (response (clojure.java.io/input-stream
                   (clojure.java.io/resource "exploud.jpg"))
                  "image/jpeg"))

   (GET "/instances/:app-name"
        [app-name]
        (response {:instances (info/instances-for-application default-region app-name)}))

   (GET "/images/:app-name"
        [app-name]
        (response {:images (info/active-amis-for-app default-region app-name)}))

   (GET "/lock"
        []
        (if @locked?
          (response "Exploud is currently locked." "text/plain")
          (response "Exploud is currently unlocked." "text/plain" 404)))

   (POST "/lock"
         []
         (lock!)
         (response "" "text/plain" 204))

   (DELETE "/lock"
           []
           (unlock!)
           (response "" "text/plain" 204))

   (GET "/deployments"
        [application environment start-from start-to size from]
        (let [parameters {:application application
                          :environment environment
                          :start-from start-from
                          :start-to start-to
                          :size size
                          :from from}
              result (apply b/validate parameters v/query-param-validators)]
          (if-let [details (first result)]
            (response {:message "Query parameter validation failed"
                       :details details} nil 400)
            (response {:deployments (store/get-deployments
                                     {:application application
                                      :environment environment
                                      :start-from (fmt/parse start-from)
                                      :start-to (fmt/parse start-to)
                                      :size (util/string->int size)
                                      :from (util/string->int from)})}))))

   (GET "/completed-deployments"
        [application environment size from]
        (let [parameters {:application application
                          :environment environment
                          :size size
                          :from from}
              result (apply b/validate parameters v/query-param-validators)]
          (if-let [details (first result)]
            (response {:message "Query parameter validation failed"
                       :details details} nil 400)
            (response {:deployments (store/get-completed-deployments
                                     {:application application
                                      :environment environment
                                      :size (util/string->int size)
                                      :from (util/string->int from)})}))))

   (GET "/incomplete-deployments"
        []
        (response {:deployments (store/deployments-with-incomplete-tasks)}))

   (GET "/broken-deployments"
        []
        (response {:deployments (store/broken-deployments)}))

   (GET "/deployments/:deployment-id"
        [deployment-id]
        (response (store/get-deployment deployment-id)))

   (POST "/deployments/:deployment-id"
         [deployment-id deployment]
         (guarded
          (store/store-deployment (postwalk date-string-to-date (keywordize-keys deployment)))
          (response nil nil 201)))

   (DELETE "/deployments/:deployment-id"
           [deployment-id]
           (guarded
            (store/delete-deployment deployment-id)
            (response nil nil 204)))

   (GET "/applications"
        []
        (response (info/applications)))

   (GET "/applications/:application"
        [application]
        (response (info/application default-environment default-region application)))

   (PUT "/applications/:application"
        [application description email owner]
        (guarded
         (if (re-matches application-regex application)
           (let [body (info/upsert-application default-region application
                                               {:description description
                                                :email email
                                                :owner owner})]
             (response body nil 201))
           (error-response "Illegal application name" 400))))

   (POST "/applications/:application/:environment/deploy"
         [application ami environment hash message user]
         (guarded
          (one-at-a-time-please application environment
                                (register-deployment application environment)
                                (let [{:keys [id]} (dep/prepare-deployment
                                                    default-region
                                                    application
                                                    environment
                                                    (or user default-user)
                                                    ami
                                                    hash
                                                    message)]
                                  (dep/start-deployment id)
                                  (response {:id id})))))

   (POST "/applications/:application/:environment/undo"
         [application environment]
         (guarded
          (one-at-a-time-please application environment
                                (register-deployment application environment)
                                (if-let [deployment (first (store/get-deployments {:application application
                                                                                   :environment environment
                                                                                   :size 1}))]
                                  (let [{:keys [id]} (dep/prepare-undo deployment)]
                                    (dep/start-deployment id)
                                    (response {:id id}))
                                  (error-response "No previous deployment" 500)))))

   (POST "/applications/:application/:environment/rollback"
         [application environment message user]
         (guarded
          (one-at-a-time-please application environment
                                (register-deployment application environment)
                                (let [{:keys [id]} (dep/prepare-rollback
                                                    default-region
                                                    application
                                                    environment
                                                    (or user default-user)
                                                    message)]
                                  (dep/start-deployment id)
                                  (response {:id id})))))

   (GET "/environments"
        []
        (response {:environments (info/environments default-region)}))

   (GET "/tasks"
        []
        (response (with-out-str (at-at/show-schedule tasks/pool))))

   (GET "/in-progress"
        []
        (response {:deployments (sort @ongoing-deployments)}))

   (DELETE "/in-progress/:application/:environment"
           [application environment]
           (unregister-deployment application environment)))

  (route/not-found (error-response "Resource not found" 404)))

(defn wrap-cors-headers
  "Adds in `Access-Control-Allow-Headers` and `Access-Control-Allow-Origin`
   headers to every response (because I'm lazy)."
  [handler]
  (fn [request]
    (-> (handler request)
        (header "Access-Control-Allow-Headers" "Access,Origin,X-Prototype-Version,X-Requested-With")
        (header "Access-Control-Allow-Origin" "*"))))

(def app
  "Sets up our application, adding in various bits of middleware."
  (-> routes
      (instrument)
      (wrap-cors-headers)
      (wrap-error-handling)
      (wrap-ignore-trailing-slash)
      (wrap-json-response)
      (wrap-json-with-padding)
      (wrap-json-params)
      (wrap-keyword-params)
      (wrap-params)
      (expose-metrics-as-json)))
