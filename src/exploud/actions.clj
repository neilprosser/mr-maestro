(ns exploud.actions
  (:require [clojure.tools.logging :as log]
            [dire.core :refer [with-handler! with-precondition!]]
            [exploud
             [asgard :as asg]
             [onix :as onix]
             [store :as store]
             [tyranitar :as tyr]])
  (:import java.util.UUID))

(defn applications
  "The list of applications Asgard knows about."
  []
  {:names (asg/applications)})

(defn application
  "The information about a particular application."
  [region application-name]
  (if-let [full-application (asg/application region application-name)]
    (merge {:name application-name}
           (select-keys (:app full-application) [:description :email :owner]))))

(defn upsert-application
  "Upserts an application into Onix, Tyranitar and Asgard. This function can be run many times, it won't fail if the application is present in any of the stores."
  [region application-name details]
  (let [onix-application (onix/upsert-application application-name)
        tyranitar-application (tyr/upsert-application application-name)]
    (asg/upsert-application application-name details)
    (merge (application region application-name) tyranitar-application)))

(defn- add-required-params [deployment-params ami region deploy-id]
  (merge deployment-params
         {"imageId" ami
          "selectedZones" [(str region "a") (str region "b")]
          "ticket" deploy-id}))

(defn- add-action [deployment-params action]
  (merge deployment-params
         {(str "_action_" action) ""}))

(defn deploy [region application-name details]
  (let [{:keys [ami environment user]} details]
    (if-let [hash (tyr/last-commit-hash environment application-name)]
      (let [deploy-id (UUID/randomUUID)
            deployment {:ami ami
                        :application application-name
                        :environment environment
                        :hash hash
                        :id deploy-id
                        :region region
                        :start "FILL THIS IN"
                        :user user}]
        (store/store-deployment deployment)
        (if-let [deployment-params (get (tyr/deployment-params environment application-name hash) "data")]
          (comment (if (asg/auto-scaling-group-exists? region application-name)
                     (asg/auto-deploy region application-name (add-action (add-required-params deployment-params ami region deploy-id) "deploy"))
                     (asg/create-new-auto-scaling-group region application-name (add-action (add-required-params deployment-params ami region deploy-id) "save"))))
          {:status 500 :body "Could not retrieve deployment parameters."}))
      {:status 500 :body "Could not retrieve last commit hash from Tyranitar."})))

(with-precondition! #'deploy
  "The application we're deploying must exist in Onix"
  :application-in-onix
  (fn [_ a _ & args]
    (onix/application a)))

(with-precondition! #'deploy
  "The application we're deploying must exist in Asgard"
  :application-in-asgard
  (fn [r a _ & args]
    (asg/application r a)))

(with-handler! #'deploy
  {:precondition :application-in-onix}
  (fn [e & args] {:status 404 :body "Application does not exist in Onix."}))

(with-handler! #'deploy
  {:precondition :application-in-asgard}
  (fn [e & args] {:status 404 :body "Application does not exist in Asgard."}))

;(deploy "eu-west-1" "skeleton" {:ami "ami-5431d523" :environment "dev" :user "nprosser"})
;(deploy "eu-west-1" "rar" {:ami "ami-2423424" :environment "dev" :user "nprosser"})
;(asg/application "skeleton")
;(application "eu-west-1" "skeleton")
;(upsert-application "eu-west-1" "wooo" {:description "description" :email "email" :owner "owner"})
;(deploy "eu-west-1" "jeffles" {:stuff "woo"})
