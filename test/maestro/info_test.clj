(ns maestro.info-test
  (:require [maestro
             [info :refer :all]
             [lister :as lister]
             [pedantic :as pedantic]
             [tyrant :as tyr]]
            [midje.sweet :refer :all]))

(fact "that getting applications returns whatever Lister gives back"
      (applications)
      => ..applications..
      (provided
       (lister/applications)
       => ..applications..))

(fact "that getting an application returns whatever Lister gives back"
      (application "application")
      => ..app..
      (provided
       (lister/application "application")
       => ..app..))

(fact "that upserting an application attempts to put it into Lister, Tyrant and Pedantic"
      (upsert-application "region" "application" "contact@somewhere.com")
      => {:tyrant "business" :lister "business" :pedantic "business"}
      (provided
       (lister/upsert-application "application")
       => ..lister..
       (tyr/upsert-application "application")
       => {:tyrant "business"}
       (pedantic/upsert-application "application")
       => {:pedantic "business"}
       (lister/add-property "application" :contact "contact@somewhere.com")
       => nil
       (pedantic/apply-config "application")
       => ..pedantic-apply..
       (application "application")
       => {:lister "business"}))
