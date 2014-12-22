(ns maestro.identity
  (:require [ninjakoala.instance-metadata :as im]))

(def ^:private instance-identity-atom
  (atom nil))

(defn current-account-id
  []
  (:account-id @instance-identity-atom))

(defn healthy?
  []
  (some? @instance-identity-atom))

(defn init
  []
  (reset! instance-identity-atom (im/instance-identity)))
