(ns maestro.messages.health
  (:require [clojure
             [set :refer [superset?]]
             [string :as str]]
            [maestro
             [aws :as aws]
             [http :as http]
             [log :as log]
             [responses :refer :all]
             [util :as util]]))

(def default-service-port
  8080)

(def default-healthcheck-path
  "/healthcheck")

(def default-healthcheck-skip
  false)

(def default-instances-maximum-attempts
  50)

(def default-load-balancer-maximum-attempts
  150)

(defn- create-url
  [ip port healthcheck-path]
  (str "http://" ip ":" port "/" healthcheck-path))

(defn- ok-response?
  [url]
  (try
   (let [{:keys [status]} (http/simple-get url {:socket-timeout 2000})]
     (= status 200))
   (catch Exception _
     false)))

(defn- check-instance-health
  [ip port healthcheck-path]
  (let [url (create-url ip port healthcheck-path)]
    {:url url
     :successful? (ok-response? url)}))

(defn- check-instances-health
  [ip-addresses port healthcheck-path min]
  (let [check-results (map #(check-instance-health % port healthcheck-path) ip-addresses)]
    (log/write (format "%s came back [%s]." (util/pluralise (count check-results) "Healthcheck") (str/join ", " (map (fn [r] (format "%s => %s" (:url r) (:successful? r))) check-results))))
    check-results))

(defn- healthcheck-path
  [application-properties]
  (let [standard (get application-properties :healthcheck.path)
        legacy (get application-properties :service.healthcheck.path)]
    (or standard legacy default-healthcheck-path)))

(defn- skip-instance-healthcheck?
  [application-properties deployment-params]
  (let [standard (get deployment-params :skip-instance-healthcheck)
        legacy (get application-properties :service.healthcheck.skip)]
    (or standard legacy default-healthcheck-skip)))

(defn wait-for-instances-to-be-healthy
  [{:keys [parameters attempt]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name tyranitar]} state
        {:keys [application-properties deployment-params]} tyranitar
        service-port (get application-properties :service.port default-service-port)
        skip-healthcheck? (skip-instance-healthcheck? application-properties deployment-params)
        healthcheck-path (util/strip-first-forward-slash (healthcheck-path application-properties))
        max-attempts (get deployment-params :instance-healthy-attempts  default-instances-maximum-attempts)
        min (:min deployment-params)]
    (if-not state
      (do
        (log/write "No need to check for healthy instances.")
        (success parameters))
      (if skip-healthcheck?
        (do
          (log/write "Skipping healthcheck.")
          (success parameters))
        (if (zero? min)
          (do
            (log/write "Minimum number of instances is 0 so skipping healthcheck.")
            (success parameters))
          (try
            (if-let [basic-instances (seq (aws/auto-scaling-group-instances auto-scaling-group-name environment region))]
              (let [instance-ids (map :instance-id basic-instances)
                    instances (aws/instances environment region instance-ids)]
                (log/write (format "Checking health of %s [%s] using port %s and path /%s." (util/pluralise (count instances) "instance") (str/join ", " instance-ids) service-port healthcheck-path))
                (let [ip-addresses (map :private-ip-address instances)
                      check-results (check-instances-health ip-addresses service-port healthcheck-path min)
                      successful-results (count (filter :successful? check-results))]
                  (if (>= successful-results min)
                    (do
                      (log/write (format "Got %d successful %s which is equal to or above the minimum of %d." successful-results (util/pluralise successful-results "response") min))
                      (success parameters))
                    (do
                      (log/write (format "Got %d successful %s but needed at least %d (attempt %d/%d)." successful-results (util/pluralise successful-results "response") min attempt max-attempts))
                      (capped-retry-after 10000 attempt max-attempts)))))
              (do
                (log/write (format "No instances are present yet (attempt %d/%d)." attempt max-attempts))
                (capped-retry-after 10000 attempt max-attempts)))
            (catch Exception e
              (error-with e))))))))

(defn- load-balancer-healthy?
  [environment region auto-scaling-group-name instance-ids load-balancer-name]
  (let [healthy-instance-ids (->> (aws/load-balancer-health environment region load-balancer-name)
                                  (filter #(= "InService" (:state %)))
                                  (map :instance-id)
                                  set)]
    (superset? healthy-instance-ids (set instance-ids))))

(defn wait-for-load-balancers-to-be-healthy
  [{:keys [parameters attempt]}]
  (let [{:keys [environment region]} parameters
        state-key (util/new-state-key parameters)
        state (state-key parameters)
        {:keys [auto-scaling-group-name tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [selected-load-balancers]} deployment-params
        max-attempts (get deployment-params :load-balancer-healthy-attempts default-load-balancer-maximum-attempts)]
    (if-not state
      (do
        (log/write "No need to check load balancer health.")
        (success parameters))
      (if (seq selected-load-balancers)
        (try
          (let [instances (aws/auto-scaling-group-instances auto-scaling-group-name environment region)]
            (if (seq instances)
              (do
                (when (= 1 attempt)
                  (log/write (format "Waiting for health of %s [%s]" (util/pluralise (count selected-load-balancers) "load balancer") (str/join ", " selected-load-balancers))))
                (let [instance-ids (map :instance-id instances)
                      all-healthy? (every? true? (map (partial load-balancer-healthy? environment region auto-scaling-group-name instance-ids) selected-load-balancers))]
                  (if all-healthy?
                    (do
                      (log/write "All load balancer(s) are healthy.")
                      (success parameters))
                    (capped-retry-after 10000 attempt max-attempts))))
              (do
                (log/write "No instances are present - skipping healthcheck.")
                (success parameters))))
          (catch Exception e
            (error-with e)))
        (do
          (log/write "No load balancers selected - skipping healthcheck.")
          (success parameters))))))
