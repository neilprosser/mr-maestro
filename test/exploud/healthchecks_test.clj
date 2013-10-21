(ns exploud.healthchecks_test
  (:require [exploud
             [asgard :as asgard]
             [healthchecks :refer :all]
             [http :as http]
             [store :as store]
             [util :as util]]
            [midje.sweet :refer :all]))

(fact "that when checking ASG health all healthy instances returns true"
      (asg-healthy? "region" "asg" 8080 "healthcheck")
      => true
      (provided
       (asgard/instances-in-asg "region" "asg")
       => [{:instance {:privateIpAddress "100.100.100.101"}}
           {:instance {:privateIpAddress "100.100.100.102"}}]
       (http/simple-get "http://100.100.100.101:8080/healthcheck")
       => {:status 200}
       (http/simple-get "http://100.100.100.102:8080/healthcheck")
       => {:status 200}))

(fact "that when checking ASG health no healthy instances returns false"
      (asg-healthy? "region" "asg" 8080 "healthcheck")
      => false
      (provided
       (asgard/instances-in-asg "region" "asg")
       => [{:instance {:privateIpAddress "100.100.100.101"}}
           {:instance {:privateIpAddress "100.100.100.102"}}]
       (http/simple-get "http://100.100.100.101:8080/healthcheck")
       => {:status 500}
       (http/simple-get "http://100.100.100.102:8080/healthcheck")
       => {:status 500}))

(fact "that when checking ASG health one unhealthy instance returns false"
      (asg-healthy? "region" "asg" 8080 "healthcheck")
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
      (check-asg-health "region" "asg" 8080 "healthcheck" ..deploy-id.. {:log []} ..completed.. ..timed-out.. 5)
      => ..reschedule-result..
      (provided
       (asg-healthy? "region" "asg")
       => false
       (util/now-string)
       => ..now..
       (store/store-task ..deploy-id.. {:log [{:message "Checking healthcheck on port 8080 and path /healthcheck."
                                               :date ..now..}]})
       => ..store-result..
       (schedule-asg-check "region" "asg" 8080 "healthcheck" ..deploy-id.. {:log [{:message "Checking healthcheck on port 8080 and path /healthcheck."
                                                                                   :date ..now..}]} ..completed.. ..timed-out.. 4)
       => ..reschedule-result..))

(fact "that when checking ELB health all healthy instances returns true"
      (elb-healthy? "region" "elb" "asg")
      => true
      (provided
       (asgard/load-balancer "region" "elb")
       => {:instanceStates [{:autoScalingGroupName "asg"
                             :state "InService"}
                            {:autoScalingGroupName "asg"
                             :state "InService"}]}))

(fact "that when checking ELB health one unhealthy instance in ASG returns false"
      (elb-healthy? "region" "elb" "asg")
      => false
      (provided
       (asgard/load-balancer "region" "elb")
       => {:instanceStates [{:autoScalingGroupName "asg"
                             :state "InService"}
                            {:autoScalingGroupName "asg"
                             :state "Busted"}]}))

(fact "that when checking ELB health one unhealthy instance outside the ASG returns true"
      (elb-healthy? "region" "elb" "asg")
      => true
      (provided
       (asgard/load-balancer "region" "elb")
       => {:instanceStates [{:autoScalingGroupName "another-asg"
                             :state "Busted"}
                            {:autoScalingGroupName "asg"
                             :state "InService"}]}))

(fact "that checking ELB health does the right things when unhealthy"
      (check-elb-health "region" "elb" "asg" ..deploy-id.. {:log []} ..completed.. ..timed-out.. 5)
      => ..reschedule-result..
      (provided
       (elb-healthy? "region" "elb" "asg")
       => false
       (util/now-string)
       => ..now..
       (store/store-task ..deploy-id.. {:log [{:message "Checking ELB health."
                                               :date ..now..}]})
       => ..store-result..
       (schedule-elb-check "region" "elb" "asg" ..deploy-id.. {:log [{:message "Checking ELB health."
                                                                      :date ..now..}]} ..completed.. ..timed-out.. 4)
       => ..reschedule-result..))
