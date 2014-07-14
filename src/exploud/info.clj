(ns exploud.info
  "## For grabbing information about the things we're dealing with"
  (:require [exploud
             [onix :as onix]
             [tyranitar :as tyr]
             [shuppet :as shuppet]]))

(defn applications
  "The list of applications Onix knows about."
  []
  (onix/applications))

(defn application
  "The information about a particular application."
  [application-name]
  (onix/application application-name))

(defn upsert-application
  "Upserts an application into Onix, Tyranitar and Shuppet. This function
   can be run many times, it won't fail if the application is present in any of the
   stores."
  [region application-name details]
  (let [onix-application (onix/upsert-application application-name)
        tyranitar-application (tyr/upsert-application application-name)
        shuppet-application (shuppet/upsert-application application-name)]
    (onix/add-property application-name :contact (:email details))
    (shuppet/apply-config application-name)
    (merge (application application-name) tyranitar-application shuppet-application)))
