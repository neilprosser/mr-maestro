(ns exploud.web
  "## Setting up our RESTful interface"
  (:require [cheshire.core :as json]
            [clojure.string :refer [split]]
            [clojure.tools.logging :refer [info warn error]]
            [compojure
             [core :refer [defroutes context GET PUT POST DELETE]]
             [handler :as handler]
             [route :as route]]
            [environ.core :refer [env]]
            [exploud
             [deployment :as dep]
             [info :as info]
             [pokemon :as pokemon]
             [store :as store]]
            [metrics.ring
             [expose :refer [expose-metrics-as-json]]
             [instrument :refer [instrument]]]
            [nokia.ring-utils
             [error :refer [wrap-error-handling error-response]]
             [metrics :refer [wrap-per-resource-metrics replace-outside-app
                              replace-guid replace-mongoid replace-number]]
             [ignore-trailing-slash :refer [wrap-ignore-trailing-slash]]]
            [ring.middleware
             [format-response :refer [wrap-json-response]]
             [json-params :refer [wrap-json-params]]
             [params :refer [wrap-params]]
             [keyword-params :refer [wrap-keyword-params]]]))

(def ^:dynamic *version*
  "The version of our application."
  "none")

(defn set-version!
  "Sets the version of the application."
  [version]
  (alter-var-root #'*version* (fn [_] version)))

(def default-region
  "The default region we'll deploy to (temporarily)."
  "eu-west-1")

(def default-user
  "The default user we'll say deployments came from (if one isn't provided)."
  "exploud")

(defn response
  "Accepts a body an optionally a content type and status. Returns a response object."
  [body & [content-type status]]
  (if body
    {:status (or status 200)
     :headers {"Content-Type" (or content-type "application/json; charset=utf-8")}
     :body body}
    {:status 404}))

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
                   :status true}))

   (GET "/pokemon"
        []
        (response pokemon/pokemon "text/plain"))

   (GET "/icon"
        []
        (response (-> (clojure.java.io/resource "exploud.jpg")
                      (clojure.java.io/input-stream)) "image/jpeg"))

   (GET "/deployments/:deployment-id"
        [deployment-id]
        (response (store/get-deployment deployment-id)))

   (GET "/applications"
        []
        (response (info/applications)))

   (GET "/applications/:application"
        [application]
        (response (info/application default-region application)))

   (PUT "/applications/:application"
        [application description email owner]
        (let [body (info/upsert-application default-region application
                                            {:description description
                                             :email email
                                             :owner owner})]
          (response body nil 201)))

   (POST "/applications/:application/deploy"
         [application ami environment]
         (let [{:keys [id] :as deployment} (dep/prepare-deployment
                                            default-region
                                            application
                                            environment
                                            default-user
                                            ami)]
           (dep/start-deployment id)
           (response {:id id}))))

  (route/not-found (error-response "Resource not found" 404)))

(def app
  "Sets up our application, adding in various bits of middleware."
  (-> routes
      (instrument)
      (wrap-error-handling)
      (wrap-ignore-trailing-slash)
      (wrap-json-params)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-json-response)
      (wrap-per-resource-metrics [replace-guid
                                  replace-mongoid
                                  replace-number
                                  (replace-outside-app "/1.x")])
      (expose-metrics-as-json)))
