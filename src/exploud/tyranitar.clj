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

;(application-properties "dev" "skeleton" "HEAD")
;(deployment-params "dev" "skeleton" "HEAD")
;(launch-data "dev" "skeleton" "HEAD")
;(last-commit-hash "dev" "skeleton")
;(get (application-properties "dev" "skeleton" "HEAD") "data")
