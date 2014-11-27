(ns maestro.naming
  "Because naming things is hard."
  (:require [clj-time
             [core :as time]
             [format :as fmt]]))

(def ^:private launch-config-pattern
  #"([a-z]+)-([a-z]+)-v([0-9]+)-([0-9]{14})")

(def ^:private launch-config-date-format
  (fmt/formatter "yyyyMMddHHmmss"))

(def ^:private asg-name-pattern
  #"([a-z]+)-([a-z]+)(?:-v([0-9]+))?")

(defn create-launch-config-name
  [{:keys [application environment iteration date]}]
  (let [date-string (fmt/unparse launch-config-date-format (or date (time/now)))]
    (format "%s-%s-v%03d-%s" application environment iteration date-string)))

(defn new-launch-config-name
  [asg-info]
  (let [now (time/now)]
    (create-launch-config-name (assoc asg-info :date now))))

(defn create-asg-name
  [{:keys [application environment iteration]}]
  (format "%s-%s-v%03d" application environment iteration))

(defn launch-config-info
  [launch-config-name]
  (let [matches (re-matches launch-config-pattern launch-config-name)
        [_ application environment iteration date] matches]
    {:application application
     :environment environment
     :iteration (Integer/parseInt iteration)
     :date (fmt/parse launch-config-date-format date)}))

(defn asg-info
  [asg-name]
  (let [matches (re-matches asg-name-pattern asg-name)
        [_ application environment iteration] matches]
    {:application application
     :environment environment
     :iteration (Integer/parseInt (or iteration "0"))}))

(defn next-asg-info
  [asg-name]
  (let [asg-info (asg-info asg-name)]
    (update-in asg-info [:iteration] inc)))

(defn next-asg-name
  [asg-name]
  (let [info (asg-info asg-name)]
    (create-asg-name (update-in info [:iteration] inc))))
