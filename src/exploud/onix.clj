(ns exploud.onix
  (:require [environ.core :refer [env]]
            [exploud.http :as http]))

(def onix-url
  (env :service-onix-url))

(defn application-exists? [application-name]
  (let [url (str onix-url "/1.x/applications/" application-name)
        {:keys [status]} (http/simple-get url)]
    (= status 200)))

;(application-exists? "skeleton")
