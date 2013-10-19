(ns exploud.unit.actions
  (:require [exploud
             [actions :refer :all]
             [asgard :as asg]
             [onix :as onix]
             [store :as store]
             [tyranitar :as tyr]]
            [midje.sweet :refer :all]))

(comment
  (def details {:ami ..ami..
                :environment ..environment..
                :user ..user..})

  (against-background
   [(asg/application ..region.. ..application..) => ..asgard..
    (onix/application ..application..) => ..onix..
    (store/store-deployment {:ami ..ami..
                             :application ..application..
                             :environment ..environment..
                             :hash ..hash..
                             :id anything
                             :region ..region..
                             :user ..user..}) => ..deploy-id..
                             (tyr/deployment-params ..environment.. ..application.. ..hash..) => {"data" {}}
                             (tyr/last-commit-hash ..environment.. ..application..) => ..hash..]

   (fact "Application missing from Onix gives 404 with correct body"
         (deploy ..region.. ..application.. details) => (contains {:status 404
                                                                   :body "Application does not exist in Onix."})
         (provided
          (onix/application ..application..) => nil))

   (fact "Application missing from Asgard gives 404 with correct body"
         (deploy ..region.. ..application.. details) => (contains {:status 404
                                                                   :body "Application does not exist in Asgard."})
         (provided
          (asg/application ..region.. ..application..) => nil))

   (fact "Failure to retrieve last commit hash from Tyranitar gives 500 with correct body"
         (deploy ..region.. ..application.. details) => (contains {:status 500
                                                                   :body "Could not retrieve last commit hash from Tyranitar."})
         (provided
          (tyr/last-commit-hash ..environment.. ..application..) => nil))

   (fact "Failure to store configuration gives 500 with correct body"
         (deploy ..region.. ..application.. details) => (contains {:status 500
                                                                   :body "Could not store deployment information."})
         (provided
          (store/store-deployment {:ami ..ami..
                                   :application ..application..
                                   :environment ..environment..
                                   :hash ..hash..
                                   :id anything
                                   :region ..region..
                                   :user ..user..}) => nil))

   (fact "Failure to retrieve deployment parameters from Tyranitar gives 500 with correct body"
         (deploy ..region.. ..application.. details) => (contains {:status 500
                                                                   :body "Could not retrieve deployment parameters."})
         (provided
          (tyr/deployment-params ..environment.. ..application.. ..hash..) => nil))

   (fact "No previous Auto Scaling Group creates one"
         (deploy ..region.. ..application.. details) => ..result..
         (provided
          (asg/auto-scaling-group-exists? ..region.. ..application..) => false
          (asg/create-new-auto-scaling-group ..region.. ..application.. {"imageId" ..ami..
                                                                         "selectedZones" ["..region..a" "..region..b"]
                                                                         "ticket" ..deploy-id..
                                                                         "_action_save" ""}) => ..result..))

   (fact "Already existing Auto Scaling Group does auto-deploy"
         (deploy ..region.. ..application.. details) => ..result..
         (provided
          (asg/auto-scaling-group-exists? ..region.. ..application..) => true
          (asg/auto-deploy ..region.. ..application.. {"imageId" ..ami..
                                                       "selectedZones" ["..region..a" "..region..b"]
                                                       "ticket" ..deploy-id..
                                                       "_action_deploy" ""}) => ..result..))))
