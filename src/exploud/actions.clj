(ns exploud.actions
  (:require [clojure.tools.logging :as log]
            [exploud.asgard :as asg]
            [exploud.onix :as onix]
            [exploud.store :as store]
            [exploud.tyranitar :as tyr]))

(defn- matches-version? [image version]
  (let [tags (:tags image)]
    (seq (filter (fn [tag] (and (= (:key tag) "version")
                               (= (:value tag) version))) tags))))
(defn image-list-by-version [region application-name version]
  (let [image-list (asg/image-list region application-name)]
    (filter (fn [image] (matches-version? image version)) image-list)))

(defn applications []
  {:names (asg/applications)})

(defn application [region application-name]
  (if-let [full-application (asg/application region application-name)]
    (merge {:name application-name}
           (select-keys (:app full-application) [:description :email :owner]))))

(defn upsert-application [region application-name details]
  (asg/upsert-application application-name details)
  (application region application-name))

; TODO - Think of better names for these *-deploy functions
(defn- manual-deploy [region application-name deployment-params ami deploy-id]
  (asg/manual-deploy region application-name (merge deployment-params {"imageId" ami
                                                                       "selectedZones" [(str region "a") (str region "b")]
                                                                       "ticket" deploy-id
                                                                       "_action_save" ""})))

(defn- auto-deploy [region application-name deployment-params ami deploy-id]
  (asg/auto-deploy region application-name (merge deployment-params {"imageId" ami
                                                                     "selectedZones" [(str region "a") (str region "b")]
                                                                     "ticket" deploy-id
                                                                     "_action_deploy" ""})))

(defn deploy [region application-name details]
  (if (onix/application-exists? application-name)
    (if (asg/application region application-name)
      (let [{:keys [ami environment user]} details]
        (if-let [hash (tyr/last-commit-hash environment application-name)]
          (let [configuration {:ami ami
                               :environment environment
                               :hash hash
                               :region region
                               :user user}]
            (log/info "Configuration is" configuration)
            (if-let [deploy-id (store/store-configuration application-name configuration)]
              (if-let [deployment-params (get (tyr/deployment-params environment application-name hash) "data")]
                (if (asg/auto-scaling-group-exists? region application-name)
                  (auto-deploy region application-name deployment-params ami deploy-id)
                  (manual-deploy region application-name deployment-params ami deploy-id))
                {:status 500 :body "Could not retrieve deployment parameters."})
              {:status 500 :body "Could not store deployment information."}))
          {:status 500 :body "Could not retrieve last commit hash."}))
      {:status 404 :body "Application does not exist in Asgard."})
    {:status 404 :body "Application does not exist in Onix."}))

;(deploy "eu-west-1" "skeleton" {:ami "ami-5431d523" :environment "dev" :user "nprosser"})
;(deploy "eu-west-1" "rar" {:ami "ami-2423424" :environment "dev" :user "nprosser"})
;(asg/application "skeleton")
;(application "skeleton")
;(upsert-application "eu-west-1" "something" {:description "description" :email "email" :owner "owner"})
