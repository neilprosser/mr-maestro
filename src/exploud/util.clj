(ns exploud.util
  "## Some helper functions")

(defn list-from
  "If `thing` is a collection we'll get it back, otherwise we stick some square brackets around it. I'm __almost certain__ there should be a function in `clojure.core` for this, but I still can't find it. If you know what it is I want, __PLEASE__ let Neil know!"
  [thing]
  (if (coll? thing) thing [thing]))
