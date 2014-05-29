(ns exploud.log-test
  (:require [clj-time.core :as time]
            [exploud
             [bindings :refer :all]
             [elasticsearch :as es]
             [log :refer :all]
             [util :as util]]
            [midje.sweet :refer :all]))

(fact "that we only write to the log if *deployment-id* is bound"
      (write "message") => nil
      (provided
       (write* anything "message") => nil :times 0))

(fact "that when we do write to the log and we have a deployment ID we pass it through"
      (binding [*deployment-id* "deployment-id"]
        (write "message") => ..result..
        (provided
         (util/generate-id) => "log-id"
         (time/now) => "now"
         (es/write-log "log-id" "deployment-id" {:date "now"
                                                 :message "message"}) => ..result..)))
