(ns maestro.environments
  (:require [clojure.tools.logging :refer [warn]]
            [maestro.lister :as lister]
            [ninjakoala.ttlr :as ttlr]))

(defn environments
  []
  (ttlr/state :environments))

(defn- map-by-name-kw
  [list]
  (apply merge (map (fn [v] {(keyword (:name v)) v}) list)))

(defn environment
  [environment-name]
  (get (environments) (keyword environment-name)))

(defn update-environments
  []
  (map-by-name-kw (map lister/environment (lister/environments))))

(defn should-notify?
  [environment-name]
  (when-let [e (environment environment-name)]
    (get-in e [:metadata :deployment-notifications])))

(defn prod-account?
  [environment-name]
  (when-let [e (environment environment-name)]
    (= "prod" (get-in e [:metadata :account]))))

(defn healthy?
  []
  (not (zero? (count (keys (environments))))))

(defn init
  []
  (ttlr/schedule :environments update-environments (* 1000 60 30) (update-environments)))
