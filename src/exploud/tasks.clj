(ns exploud.tasks
  (:require [overtone.at-at :as at-at]))

(def pool
  "Our trusty task pool. Used for tracking healthchecks and keeping Asgard tasks
   up to date."
  (at-at/mk-pool))
