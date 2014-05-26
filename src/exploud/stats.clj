(ns exploud.stats
  (:require [clj-time.core :as time]
            [exploud.elasticsearch :as es]))

(defn deployments-collection
  []
  "deployments")

(defn deployments-by-user
  []
  (es/deployments-by-user))

(defn deployments-by-application
  []
  (es/deployments-by-application))

(defn deployments-by-month
  []
  (es/deployments-by-month))

(defn deployments-by-day
  []
  (es/deployments-by-day))

(defn deployments-in-environment-by-month
  [environment]
  (es/deployments-in-environment-by-month environment))

(defn deployments-in-environment-by-day
  [environment]
  (es/deployments-in-environment-by-day environment))
