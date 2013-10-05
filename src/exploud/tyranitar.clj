(ns exploud.tyranitar
  (:require [cheshire.core :as json]
            [environ.core :refer [env]]
            [exploud.http :as http]))

(def tyranitar-url
  (env :service-tyranitar-url))

(defn- file-url [environment application-name commit-hash file-name]
  (str tyranitar-url "/1.x/applications/" environment "/" application-name "/" commit-hash "/" file-name))

(defn- commits-url [environment application-name]
  (str tyranitar-url "/1.x/applications/" environment "/" application-name))

(defn- applications-url []
  (str tyranitar-url "/1.x/applications"))

(defn- get-file-content [environment application-name commit-hash file-name]
  (let [{:keys [body status]} (http/simple-get (file-url environment application-name commit-hash file-name))]
    (if (= status 200)
      (json/parse-string body))))

(defn application-properties [environment application-name commit-hash]
  (if-let [content (get-file-content environment application-name commit-hash "application-properties")]
    content
    {:status 500 :body "Application properties not found. Deployment cannot continue."}))

(defn deployment-params [environment application-name commit-hash]
  (if-let [content (get-file-content environment application-name commit-hash "deployment-params")]
    content
    {:status 500 :body "Deployment params not found. Deployment cannot continue."}))

(defn launch-data [environment application-name commit-hash]
  (if-let [content (get-file-content environment application-name commit-hash "launch-data")]
    content
    {:status 500 :body "Launch data not found. Deployment cannot continue."}))

(defn commits [environment application-name]
  (let [{:keys [body status]} (http/simple-get (commits-url environment application-name))]
    (if (= status 200)
      (json/parse-string body true))))

(defn last-commit-hash [environment application-name]
  (:hash (first (:commits (commits environment application-name)))))

(defn create-application [application-name]
  (let [content (json/generate-string {:name application-name})
        response (http/simple-post (applications-url) {:content-type :json :body content :socket-timeout 180000})
        {:keys [body status]} response]
    (if (= status 201)
      (json/parse-string body true)
      {:status 500 :body "Could not create application in Tyranitar."})))

(defn application [application-name]
  (let [{:keys [body status]} (http/simple-get (applications-url))]
    ((keyword application-name) (:applications (json/parse-string body true)))))

(defn upsert-application [application-name]
  (if-let [application (application application-name)]
    application
    (create-application application-name)))

;(application-properties "dev" "skeleton" "HEAD")
;(deployment-params "dev" "skeleton" "HEAD")
;(launch-data "dev" "skeleton" "HEAD")
;(last-commit-hash "dev" "skeleton")
;(get (application-properties "dev" "skeleton" "HEAD") "data")
;(create-application "neiltest")
;(application "matttest")
;(application "skeleton")
;(upsert-application "wooo")
