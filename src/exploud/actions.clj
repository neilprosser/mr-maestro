(ns exploud.actions
  (:require [exploud.asgard :as asg]))

(def region "eu-west-1")

(defn deploy [application-name]
  (asg/deploy-for-great-success region application-name))
