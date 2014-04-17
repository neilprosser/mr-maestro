(ns exploud.util
  "## Some helper functions"
  (:require [clj-time.core :as time]
            [clojure
             [string :as str]
             [walk :as walk]])
  (:import java.util.UUID))

(defn clojurize
  "Takes a value, obtains the `name` of it and converts any camel-case to hyphenated."
  [v]
  (keyword (str/replace (name v) #"([a-z])([A-Z])" (fn [[_ end start]] (str (str/lower-case end) "-" (str/lower-case start))))))

(defn clojurize-keys
  [m]
  (let [f (fn [[k v]] [(clojurize k) v])]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn remove-nil-values
  [m]
  (apply hash-map (flatten (remove (fn [[k v]] (nil? v)) m))))

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
    {:image-name name
     :application (nth matches 1)
     :version (nth matches 2)
     :iteration (nth matches 3)
     :bake-date (time/date-time (string->int (nth matches 4)) (string->int (nth matches 5)) (string->int (nth matches 6)) (string->int (nth matches 7)) (string->int (nth matches 8)) (string->int (nth matches 9)))}))

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

(defn as-table
  "Prints a collection of maps in a textual table. Prints table headings
   ks, and then a line of output for each row, corresponding to the keys
   in ks. If ks are not specified, use the keys of the first item in rows."
  ([ks rows]
     (with-out-str
      (when (seq rows)
        (let [widths (map
                      (fn [k]
                        (apply max (count (str k)) (map #(count (str (get % k))) rows)))
                      ks)
              spacers (map #(apply str (repeat % "-")) widths)
              fmts (map #(str "%" % "s") widths)
              fmt-row (fn [leader divider trailer row]
                        (str leader
                             (apply str (interpose divider
                                                   (for [[col fmt] (map vector (map #(get row %) ks) fmts)]
                                                     (format fmt (str col)))))
                             trailer))]
          (println (fmt-row "" "\t" "" (zipmap ks (map name ks))))
          (doseq [row rows]
            (println (fmt-row "" "\t" "" row)))))))
  ([rows] (as-table (keys (first rows)) rows)))

(defn map-by-property
  [property list]
  (apply merge (map (fn [v] {(property v) v}) list)))

(defn pluralise
  [value word]
  (if (= 1 value)
    word
    (str word "s")))

(defn previous-state-key
  [{:keys [undo]}]
  (if-not undo
    :previous-state
    :new-state))

(defn new-state-key
  [{:keys [undo]}]
  (if-not undo
    :new-state
    :previous-state))
