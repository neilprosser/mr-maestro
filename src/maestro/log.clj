(ns maestro.log
  (:require [clj-time.core :as time]
            [maestro
             [bindings :refer :all]
             [elasticsearch :as es]
             [util :as util]]))

(defn write*
  [deployment-id message-text]
  (let [log-id (util/generate-id)
        message {:date (str (time/now)) :message message-text}]
    (es/write-log log-id deployment-id message)))

(defn write
  [message-text]
  (when-let [deployment-id *deployment-id*]
    (write* deployment-id message-text)))
