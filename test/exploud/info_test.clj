(ns exploud.info_test
  (:require [exploud
             [asgard :as asgard]
             [info :refer :all]
             [onix :as onix]
             [tyranitar :as tyr]
             [shuppet :as shuppet]]
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
      (upsert-application "region" "application" {:email "contact@somewhere.com"})
      => {:tyranitar "business" :asgard "business" :shuppet "business"}
      (provided
       (onix/upsert-application "application")
       => ..onix..
       (tyr/upsert-application "application")
       => {:tyranitar "business"}
       (shuppet/upsert-application "application")
       => {:shuppet "business"}
       (onix/add-property "application" :contact "contact@somewhere.com")
       => nil
       (asgard/upsert-application "application" {:email "contact@somewhere.com"})
       => ..asgard..
       (shuppet/apply-config "application")
       => ..shuppet-apply..
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

(def configs-list [{:launchConfigurationName "wibble-poke-v004-20131101110801"}
                   {:launchConfigurationName "wobble-poke-v004-20131101110801"}
                   {:launchConfigurationName "wabble-poke-v004-20131101110801"}
                   {:launchConfigurationName "wibble-poke-v003-20131030110801"}])

(def active-launch-config {:image {:imageId "ami-07fe1d70"} :group {:createdTime "2013-10-30T09:10:47Z"}})

(def inactive-launch-config {:image {:imageId "ami-17fe1d71"} :group nil})

(fact "that correct active ami is found for application."
      (active-amis-for-app anything "wibble") => '({:imageId "ami-07fe1d70"})
      (provided
       (asgard/launch-config-list anything) => configs-list
       (asgard/launch-config anything "wibble-poke-v004-20131101110801") => active-launch-config
       (asgard/launch-config anything "wibble-poke-v003-20131030110801") => inactive-launch-config))

(fact "that no active ami is found for non-existent application."
      (active-amis-for-app anything "waffle") => '()
      (provided
       (asgard/launch-config-list anything) => configs-list))

(fact "that we can get environments"
      (environments "region")
      => ..environments..
      (provided
       (asgard/stacks "region")
       => ..environments..))
