(ns exploud.actions
  (:require [clojure.tools.logging :as log]
            [exploud.asgard :as asg]
            [exploud.onix :as onix]
            [exploud.store :as store]
            [exploud.tyranitar :as tyr]))

(defn create-params [file-params user-params]
  (for [[k v] (seq (merge file-params user-params))
        vs (flatten (conj [] v))]
    [k vs]))

(defn- matches-version? [image version]
  (let [tags (:tags image)]
    (seq (filter (fn [tag] (and (= (:key tag) "version")
                               (= (:value tag) version))) tags))))
(defn image-list-by-version [region application-name version]
  (let [image-list (asg/image-list region application-name)]
    (filter (fn [image] (matches-version? image version)) image-list)))

(defn applications []
  {:names (asg/applications)})

(defn deploy [region application-name details]
  (if (onix/application-exists? application-name)
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
              (asg/deploy region application-name (create-params deployment-params {"imageId" ami
                                                                                    "selectedZones" [(str region "a") (str region "b")]
                                                                                    "ticket" deploy-id
                                                                                    "_action_deploy" ""}))
              {:status 500 :body "Could not retrieve deployment parameters."})
            {:status 500 :body "Could not store deployment information."}))
        {:status 500 :body "Could not retrieve last commit hash."}))
    {:status 404 :body "Application does not exist."}))

;(deploy "eu-west-1" "skeleton" {:ami "ami-5431d523" :environment "dev" :user "nprosser"})
