(ns maestro.userdata
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [maestro.util :as util]))

(defn- write-to-file
  [file lines]
  (str/join "\n" (flatten (conj [(format "cat > %s <<EOF" file)]
                                lines
                                ["EOF"]))))

(defn- symlink-file
  [source-path dest-path]
  (format "ln -s %s %s" source-path dest-path))

(defn- export
  [var value]
  (format "export %s=%s" var value))

(def ^:private subscriptions-directory
  "/etc/sensu/conf.d/subscriptions.d")

(defn get-application-config
  [parameters]
  (-> (util/new-state-key parameters)
      parameters
      (get-in [:tyranitar :application-config])))

(defn build-app-config-path
  [{:keys [application] :as parameters}]
  (when (get-application-config parameters)
    (format "/etc/%s-config.json" application)))

(defn- create-environment-variables
  [{:keys [application environment region] :as parameters}]
  (let [state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name hash launch-configuration-name]} state]
    (->> {:APP_CONFIG_PATH (build-app-config-path parameters)
          :CLOUD_APP application
          :CLOUD_STACK environment
          :CLOUD_CLUSTER (format "%s-%s" application environment)
          :CLOUD_AUTO_SCALE_GROUP auto-scaling-group-name
          :CLOUD_LAUNCH_CONFIG launch-configuration-name
          :EC2_REGION region
          :HASH hash}
         util/remove-nil-values
         (into (sorted-map))
         (filter (fn [[k v]] (some? v)))
         (map (fn [[k v]] (export (name k) v)))
         (write-to-file "/etc/profile.d/asgard.sh"))))

(defn- create-subscriptions
  [{:keys [application] :as parameters}]
  (let [state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [subscriptions]} deployment-params]
    (when subscriptions
      (str/join "\n" [(str "mkdir -p " subscriptions-directory)
                      (->> {:client {:subscriptions subscriptions}}
                           json/generate-string
                           (write-to-file (format "%s/%s.json" subscriptions-directory application)))]))))

(defn- create-application-properties
  [{:keys [application] :as parameters}]
  (let [state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [tyranitar]} state
        {:keys [application-properties]} tyranitar]
    (->> application-properties
         (into (sorted-map))
         (map (fn [[k v]] (format "%s=%s" (name k) (str/replace v "$" "\\$"))))
         (write-to-file (format "/var/encrypted/properties/%s.properties" application)))))

(defn- link-application-properties
  [{:keys [application]}]
  (let [application-properties-path (format "/var/encrypted/properties/%s.properties" application)]
    (symlink-file application-properties-path "/etc/")))

(defn- create-optional-application-config
  [{:keys [application] :as parameters}]
  (when-let [application-config (get-application-config parameters)]
    (let [application-config-directory "/var/encrypted/config"
          application-config-filename (format "%s-config.json" application)
          application-config-path (format "%s/%s" application-config-directory application-config-filename)]
      (str/join "\n" [(str "mkdir -p " application-config-directory)
                      (write-to-file application-config-path (json/generate-string application-config))
                      (symlink-file application-config-path "/etc/")]))))

(defn- create-launch-data
  [parameters]
  (let [state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [tyranitar]} state
        {:keys [launch-data]} tyranitar]
    (str/join "\n" launch-data)))

(defn create-user-data
  [parameters]
  (str/join "\n" (remove nil? ["#!/bin/bash"
                               (create-environment-variables parameters)
                               (create-subscriptions parameters)
                               (create-application-properties parameters)
                               (link-application-properties parameters)
                               (create-optional-application-config parameters)
                               (create-launch-data parameters)])))
