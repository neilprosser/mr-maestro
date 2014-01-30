(ns exploud.util
  "## Some helper functions"
  (:require [clj-time.core :as time])
  (:import java.util.UUID))

(defn string->int
  "Attempts to turn a string into an integer, or nil if not an integer."
  [s]
  (when s
    (try
      (Integer/parseInt (str s))
      (catch Exception e
        nil))))

(defn ami-details
  "Extracts details from the name of an AMI in the form ent-{app}-{version}-{iteration}-{year}-{month}-{day}_{hour}-{minute}-{second}"
  [name]
  (let [matches (re-find #"^ent-([^-]+)-([\.0-9]+)-([0-9]+)-([0-9]{4})-([0-9]{2})-([0-9]{2})_([0-9]{2})-([0-9]{2})-([0-9]{2})$" name)]
    {:name (nth matches 1) :version (nth matches 2) :iteration (nth matches 3) :bake-date (time/date-time (string->int (nth matches 4)) (string->int (nth matches 5)) (string->int (nth matches 6)) (string->int (nth matches 7)) (string->int (nth matches 8)) (string->int (nth matches 9)))}))

(defn generate-id
  "Create a random ID for a deployment or task."
  []
  (str (UUID/randomUUID)))

(defn list-from
  "If `thing` is a collection we'll get it back, otherwise we make a list with
   `thing` as the only item. I'm __almost certain__ there must be a function in
   `clojure.core` for this, but I still can't find it. If you know what it is I
   want, __PLEASE__ let Neil know!"
  [thing]
  (cond (coll? thing) thing
        (nil? thing) []
        :else [thing]))

(defn strip-first-forward-slash
  "Turns `/this/that` into `this/that`."
  [thing]
  (second (re-find #"^/*(.+)" thing)))

(defn append-to-task-log
  "Appends the given message to the task's `:log`, creating a new one if it
   doesn't exist."
  [message task]
  (let [updated-log (conj (vec (:log task)) {:message message :date (time/now)})]
    (assoc task :log updated-log)))
