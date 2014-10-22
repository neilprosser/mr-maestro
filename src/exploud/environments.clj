(ns exploud.environments
  (:require [clojure.tools.logging :refer [warn]]
            [exploud.onix :as onix]
            [overtone.at-at :as at]))

(def ^:private pool
  (atom nil))

(def environments-atom
  (atom nil))

(defn create-pool
  []
  (when-not @pool
    (reset! pool (at/mk-pool :cpu-count 1))))

(defn environments
  []
  @environments-atom)

(defn environment
  [environment-name]
  (get (environments) (keyword environment-name)))

(defn- map-by-name-kw
  [list]
  (apply merge (map (fn [v] {(keyword (:name v)) v}) list)))

(defn update-environments
  []
  (try
    (when-let [environments (map-by-name-kw (map onix/environment (onix/environments)))]
      (reset! environments-atom environments))
    (catch Exception e
      (warn e "Failed to update environments"))))

(defn prod-account?
  [environment-name]
  (when-let [e (environment environment-name)]
    (= "prod" (get-in e [:metadata :account]))))

(defn init
  []
  (create-pool)
  (at/interspaced (* 1000 60 30) update-environments @pool :initial-delay 0))
