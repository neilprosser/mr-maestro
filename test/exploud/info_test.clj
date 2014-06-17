(ns exploud.info-test
  (:require [exploud
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

(fact "that getting an application returns whatever Onix gives back"
      (application "application")
      => ..app..
      (provided
       (onix/application "application")
       => ..app..))

(fact "that upserting an application attempts to put it into Onix, Tyranitar"
      (upsert-application "region" "application" {:email "contact@somewhere.com"})
      => {:tyranitar "business" :onix "business" :shuppet "business"}
      (provided
       (onix/upsert-application "application")
       => ..onix..
       (tyr/upsert-application "application")
       => {:tyranitar "business"}
       (shuppet/upsert-application "application")
       => {:shuppet "business"}
       (onix/add-property "application" :contact "contact@somewhere.com")
       => nil
       (shuppet/apply-config "application")
       => ..shuppet-apply..
       (application "application")
       => {:onix "business"}))

(fact "that we can get environments"
      (environments) => ["poke" "prod"]
      (provided
       (onix/environments) => ["poke" "prod"]))

(fact "that we can get an environment"
      (environment "env") => ..env..
      (provided
       (onix/environment "env") => ..env..))
