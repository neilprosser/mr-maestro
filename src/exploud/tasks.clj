(ns exploud.tasks
  (:require [exploud.redis :as redis]))

(defn enqueue
  [task]
  (redis/enqueue task))
