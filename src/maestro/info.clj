(ns maestro.info
  "## For grabbing information about the things we're dealing with"
  (:require [maestro
             [lister :as lister]
             [pedantic :as pedantic]
             [tyrant :as tyr]]))

(defn applications
  "The list of applications Lister knows about."
  []
  (lister/applications))

(defn application
  "The information about a particular application."
  [application-name]
  (lister/application application-name))

(defn upsert-application
  "Upserts an application into Lister, Tyrant and Pedantic. This function
   can be run many times, it won't fail if the application is present in any of the
   stores."
  [region application-name email]
  (let [lister-application (lister/upsert-application application-name)
        tyrant-application (tyr/upsert-application application-name)
        pedantic-application (pedantic/upsert-application application-name)]
    (lister/add-property application-name :contact email)
    (pedantic/apply-config application-name)
    (merge (application application-name) tyrant-application pedantic-application)))
