(ns exploud.util
  "## Some helper functions"
  (:require [clj-time.core :as time])
  (:import java.util.UUID))

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
  (cond
   (coll? thing)
   thing
   (nil? thing)
   []
   :else
   [thing]))

(defn string->number
  "Attempts to turn a string into a number, or nil if not a number."
  [string]
  (if string
    (let [n (read-string (str string))]
      (when (number? n) n))))

(defn strip-first-forward-slash
  "Turns `/this/that` into `this/that`."
  [thing]
  (second (re-find #"^/*(.+)" thing)))
