(ns maestro.messages.healthy
  (:require [maestro
             [healthy :as healthy]
             [log :as log]
             [responses :refer :all]
             [util :as util]]))

(defn- uses-healthy?
  [{:keys [healthy]}]
  (some? healthy))

(defn register-with-healthy
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name tyranitar]} state
        {:keys [application-properties deployment-params]} tyranitar
        path (get-in deployment-params [:healthy :path])
        port (or (get-in deployment-params [:healthy :port]) (get application-properties :service.port))
        scheme (get-in deployment-params [:healthy :scheme])
        timeout (get-in deployment-params [:healthy :timeout])]
    (if (uses-healthy? deployment-params)
      (do
        (log/write (format "Registering '%s' with Healthy." auto-scaling-group-name))
        (if (healthy/register-auto-scaling-group environment region auto-scaling-group-name path port scheme timeout)
          (success parameters)
          (do
            (log/write (format "Failed to register '%s' with Healthy." auto-scaling-group-name))
            (success parameters))))
      (success parameters))))

(defn deregister-from-healthy
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state-key (util/previous-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name tyranitar]} state
        {:keys [deployment-params]} tyranitar]
    (if (and (uses-healthy? deployment-params) auto-scaling-group-name)
      (do
        (log/write (format "Deregistering '%s' from Healthy." auto-scaling-group-name))
        (if (healthy/deregister-auto-scaling-group environment region auto-scaling-group-name)
          (success parameters)
          (do
            (log/write (format "Failed to deregister '%s' from Healthy." auto-scaling-group-name))
            (success parameters))))
      (success parameters))))
