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
      (application "environment" "region" "application")
      => nil
      (provided
       (asgard/application "environment" "region" "application")
       => nil))

(fact "that getting an application returns whatever Asgard gives back and munges it a bit"
      (application "environment" "eu-west-1" "application")
      => {:description "description"
          :email "email"
          :name "application"
          :owner "owner"}
      (provided
       (asgard/application "environment" "eu-west-1" "application")
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
       (application "poke" "region" "application")
       => {:asgard "business"}))

(def instances [{:ec2Instance {:tags [{:key "Name" :value "myapp"} {:key "color" :value "red"}]}}
                {:ec2Instance {:tags [{:key "Name" :value "other"} {:key "color" :value "red"}]}}
                {:ec2Instance {:tags [{:key "Name" :value "another"} {:key "color" :value "green"}]}}])

(fact "that named instance is found"
      (instances-for-application anything "myapp") => '({:ec2Instance
                                                         {:tags
                                                          [{:key "Name"
                                                            :value "myapp"}
                                                           {:key "color"
                                                            :value "red"}]}})
      (provided
       (asgard/all-instances anything) => instances))

(fact "that empty list is returned when no app of given name exists."
      (instances-for-application anything "wibble") => '()
      (provided
       (asgard/all-instances anything) => instances))
