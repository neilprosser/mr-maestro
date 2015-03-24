(ns maestro.util
  "## Some helper functions"
  (:require [clj-time.core :as time]
            [clojure
             [string :as str]
             [walk :as walk]]
            [org.tobereplaced.lettercase :refer [lower-hyphen-keyword]])
  (:import java.util.UUID))

(defn to-params
  [m]
  (mapcat (fn [[k v]] [k v]) m))

(def t2-pattern
  #"^t2\..+$")

(defn has-cpu-credits?
  [instance-type]
  (re-matches t2-pattern instance-type))

(defn char-for-index
  [index]
  (char (+ 97 index)))

(defn clojurize-keys
  [m]
  (let [f (fn [[k v]] [(lower-hyphen-keyword (name k)) v])]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn remove-nil-values
  [m]
  (into {} (remove (comp nil? second) m)))

(defn string->int
  "Attempts to turn a string into an integer, or nil if not an integer."
  [s]
  (when s
    (try
      (Integer/parseInt (str s))
      (catch Exception e
        nil))))

(defn- create-tags
  [tags]
  (into {} (map (fn [t] [(lower-hyphen-keyword (:key t)) (:value t)]) tags)))

(defn image-details
  "Extracts details from the name of an image in the form ent-{app}-{version}-{iteration}-{year}-{month}-{day}_{hour}-{minute}-{second}"
  [{:keys [name tags]}]
  (when name
    (when-let [matches (re-find #"ent-([^-]+)-([\.0-9]+)-([0-9]+)-(?:([a-z]+)-)?([0-9]{4})-([0-9]{2})-([0-9]{2})_([0-9]{2})-([0-9]{2})-([0-9]{2})$" name)]
      {:image-name name
       :application (nth matches 1)
       :version (nth matches 2)
       :iteration (nth matches 3)
       :virt-type (or (nth matches 4) "para")
       :bake-date (time/date-time (string->int (nth matches 5)) (string->int (nth matches 6)) (string->int (nth matches 7)) (string->int (nth matches 8)) (string->int (nth matches 9)) (string->int (nth matches 10)))
       :tags (create-tags tags)})))

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
  (second (re-find #"^/*(.*)" thing)))

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
               spacers (map #(str/join (repeat % "-")) widths)
               fmts (map #(str "%" % "s") widths)
               fmt-row (fn [leader divider trailer row]
                         (str leader
                              (str/join divider
                                        (for [[col fmt] (map vector (map #(get row %) ks) fmts)]
                                          (format fmt (str col))))
                              trailer))]
           (println (fmt-row "" "\t" "" (zipmap ks (map name ks))))
           (doseq [row rows]
             (println (fmt-row "" "\t" "" row)))))))
  ([rows] (as-table (keys (first rows)) rows)))

(defn map-by-property
  [property values]
  (apply merge (map (fn [v] {(property v) v}) values)))

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
