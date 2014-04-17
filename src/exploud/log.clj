(ns exploud.log
  (:require [clj-time.core :as time]
            [exploud
             [bindings :refer :all]
             [elasticsearch :as es]
             [util :as util]]))

(defn write
  [message-text]
  (when-let [deployment-id *deployment-id*]
    (let [log-id (util/generate-id)
          message {:date (str (time/now)) :message message-text}]
      (es/write-log log-id deployment-id message))))
