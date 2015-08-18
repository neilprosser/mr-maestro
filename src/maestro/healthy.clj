(ns maestro.healthy
  (:require [cheshire.core :as json]
            [clojure.tools.logging :refer [warn]]
            [environ.core :refer [env]]
            [io.clj.logging :refer [with-logging-context]]
            [maestro
             [environments :as environments]
             [http :as http]
             [util :as util]]))

(defn- monitor-url
  [healthy-url region asg-name]
  (str healthy-url "/1.x/monitors/regions/" region "/asgs/" asg-name))

(defn register-auto-scaling-group
  [environment region asg-name path port scheme timeout]
  (if-let [healthy-url (environments/healthy-url environment)]
    (let [body (util/remove-nil-values {:path path
                                        :port port
                                        :scheme scheme
                                        :timeout timeout})]
      (try
        (let [{:keys [status]} (http/simple-put (monitor-url healthy-url region asg-name) {:body (json/generate-string body)
                                                                                           :headers {"Content-Type" "application/json"}})]
          (= status 201))
        (catch Exception e
          (warn e "Failed to register auto-scaling group with Healthy.")
          false)))
    (with-logging-context {:environment environment}
      (warn "Healthy URL not set for environment")
      false)))

(defn deregister-auto-scaling-group
  [environment region asg-name]
  (if-let [healthy-url (environments/healthy-url environment)]
    (try
      (let [{:keys [status]} (http/simple-delete (monitor-url healthy-url region asg-name))]
        (or (= status 204) (= status 404)))
      (catch Exception e
        (warn e "Failed to deregister auto-scaling group from Healthy.")
        false))
    (with-logging-context {:environment environment}
      (warn "Healthy URL not set for environment")
      false)))
