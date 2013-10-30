(ns exploud.validators
  (:require [bouncer
             [core :as b]
             [validators :as v]]
            [clj-time.format :as fmt]
            [exploud.util :as util]))

(defn zero-or-more?
  "Whether a given input is zero or more."
  [input]
  (if input
    (if-let [number (util/string->number input)]
      (or (= 0 number) (v/positive number))
      false)
    true))

(defn positive?
  "Whether a given input is a positive number."
  [input]
  (if input
    (if-let [number (util/string->number input)]
      (v/positive number)
      false)
    true))

(defn valid-date?
  "Whether the given input is a valid date."
  [input]
  (if input
    (try
      (fmt/parse input)
      (catch Exception _
        false))
    true))

(def query-param-validators
  "The validators we should `apply` to validate query parameters."
  [:start-from [[valid-date? :message "start-from must be a valid date"]]
   :start-to [[valid-date? :message "start-to must be a valid date"]]
   :size [[positive? :message "size must positive"]]
   :from [[zero-or-more? :message "from must be zero or more"]]])
