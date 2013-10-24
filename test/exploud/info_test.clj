(ns exploud.info_test
  (:require [exploud
             [asgard :as asgard]
             [info :refer :all]
             [onix :as onix]
             [tyranitar :as tyr]]
            [midje.sweet :refer :all]))

(fact "that getting applications returns whatever Onix gives back"
      (applications)
      => ..applications..
      (provided
       (onix/applications)
       => ..applications..))

(fact "that getting an application which doesn't exist returns nil"
      (application "region" "application")
      => nil
      (provided
       (asgard/application "region" "application")
       => nil))

(fact "that getting an application returns whatever Asgard gives back and munges it a bit"
      (application "eu-west-1" "application")
      => {:description "description"
          :email "email"
          :name "application"
          :owner "owner"}
      (provided
       (asgard/application "eu-west-1" "application")
       => {:app {:description "description"
                 :email "email"
                 :owner "owner"}}))

(fact "that upserting an application attempts to put it into Onix, Tyranitar and Asgard"
      (upsert-application "region" "application" ..details..)
      => {:tyranitar "business" :asgard "business"}
      (provided
       (onix/upsert-application "application")
       => ..onix..
       (tyr/upsert-application "application")
       => {:tyranitar "business"}
       (asgard/upsert-application "application" ..details..)
       => ..asgard..
       (application "region" "application")
       => {:asgard "business"}))
