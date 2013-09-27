(ns exploud.web
  (:require   [cheshire.core :as json]
              [clojure.tools.logging :as log]
              [compojure.core :refer [defroutes context GET PUT POST DELETE]]
              [compojure.route :as route]
              [compojure.handler :as handler]
              [exploud.actions :as exp]
              [exploud.store :as store]
              [ring.middleware.format-response :refer [wrap-json-response]]
              [ring.middleware.json-params :refer [wrap-json-params]]
              [ring.middleware.params :refer [wrap-params]]
              [ring.middleware.keyword-params :refer [wrap-keyword-params]]
              [clojure.string :refer [split]]
              [clojure.tools.logging :refer [info warn error]]
              [environ.core :refer [env]]
              [nokia.ring-utils.error :refer [wrap-error-handling error-response]]
              [nokia.ring-utils.metrics :refer [wrap-per-resource-metrics replace-outside-app
                                                replace-guid replace-mongoid replace-number]]
              [nokia.ring-utils.ignore-trailing-slash :refer [wrap-ignore-trailing-slash]]
              [metrics.ring.expose :refer [expose-metrics-as-json]]
              [metrics.ring.instrument :refer [instrument]]))

(def ^:dynamic *version* "none")
(defn set-version! [version]
  (alter-var-root #'*version* (fn [_] version)))

(def default-region "eu-west-1")

(def default-user "exploud")

(defroutes routes
  (context
   "/1.x" []

   (GET "/ping"
        [] {:status 200 :body "pong"})

   (GET "/status"
        [] {:status 200 :body {:name "exploud"
                               :version *version*
                               :status true}})

   (GET "/tasks/:task-id"
        [task-id] {:status 200 :body (store/get-task task-id)})

   (GET "/configurations/:configuration-id"
        [configuration-id] {:status 200 :body (store/get-configuration configuration-id)})

   (POST "/applications/:application/deploy"
         [application ami environment] {:status 200 :body (exp/deploy default-region application {:ami ami
                                                                                                  :environment environment
                                                                                                  :user default-user})}))

  (route/not-found (error-response "Resource not found" 404)))

(def app
  (-> routes
      (instrument)
      (wrap-error-handling)
      (wrap-ignore-trailing-slash)
      (wrap-json-params)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-json-response)
      (wrap-per-resource-metrics [replace-guid replace-mongoid replace-number (replace-outside-app "/1.x")])
      (expose-metrics-as-json)))
