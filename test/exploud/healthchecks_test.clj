(ns exploud.healthchecks_test
  (:require [exploud
             [asgard :as asgard]
             [healthchecks :refer :all]
             [http :as http]
             [store :as store]
             [util :as util]]
            [midje.sweet :refer :all]))

(fact "that all healthy instances returns true"
      (asg-healthy? "region" "asg")
      => true
      (provided
       (asgard/instances-in-asg "region" "asg")
       => [{:instance {:privateIpAddress "100.100.100.101"}}
           {:instance {:privateIpAddress "100.100.100.102"}}]
       (http/simple-get "http://100.100.100.101:8080/healthcheck")
       => {:status 200}
       (http/simple-get "http://100.100.100.102:8080/healthcheck")
       => {:status 200}))

(fact "that no healthy instances returns false"
      (asg-healthy? "region" "asg")
      => false
      (provided
       (asgard/instances-in-asg "region" "asg")
       => [{:instance {:privateIpAddress "100.100.100.101"}}
           {:instance {:privateIpAddress "100.100.100.102"}}]
       (http/simple-get "http://100.100.100.101:8080/healthcheck")
       => {:status 500}
       (http/simple-get "http://100.100.100.102:8080/healthcheck")
       => {:status 500}))

(fact "that one unhealth instance returns false"
      (asg-healthy? "region" "asg")
      => false
      (provided
       (asgard/instances-in-asg "region" "asg")
       => [{:instance {:privateIpAddress "100.100.100.101"}}
           {:instance {:privateIpAddress "100.100.100.102"}}]
       (http/simple-get "http://100.100.100.101:8080/healthcheck")
       => {:status 200}
       (http/simple-get "http://100.100.100.102:8080/healthcheck")
       => {:status 500}))

(fact "that checking ASG health does the right things when unhealthy"
      (check-asg-health "region" "asg" ..deploy-id.. {:log []} ..completed.. ..timed-out.. 5)
      => ..reschedule-result..
      (provided
       (asg-healthy? "region" "asg")
       => false
       (util/now-string)
       => ..now..
       (store/store-task ..deploy-id.. {:log [{:message "Polled again."
                                               :date ..now..}]})
       => ..store-result..
       (reschedule-check "region" "asg" ..deploy-id.. {:log [{:message "Polled again."
                                                              :date ..now..}]} ..completed.. ..timed-out.. 4)
       => ..reschedule-result..))
