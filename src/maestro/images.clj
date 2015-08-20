(ns maestro.images
  (:require [maestro
             [elasticsearch :as es]
             [environments :as environments]]))

(defn last-two-completed-deployment-images
  [application environment region]
  (let [deployments (es/get-deployments {:application application
                                         :environment environment
                                         :from 0
                                         :region region
                                         :size 2
                                         :status "completed"})]
    (map #(get-in % [:new-state :image-details :id]) deployments)))

(defn prohibited-images
  [application region]
  (let [environments (environments/environments)]
    (into (hash-set) (mapcat #(last-two-completed-deployment-images application (name %) region) (keys environments)))))
