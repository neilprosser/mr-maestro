(ns exploud.healthchecks_test
  (:require [clj-time.core :as time]
            [exploud
             [asgard :as asgard]
             [healthchecks :refer :all]
             [http :as http]
             [store :as store]
             [util :as util]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "that an instance is unhealthy when a connect timeout exception is thrown"
      (instance-healthy? ..instance.. "port" "healthcheck")
      => false
      (provided
       (instance-ip ..instance..)
       => "ip"
       (http/simple-get "http://ip:port/healthcheck" {:socket-timeout 2000})
       =throws=> (ex-info "Whoops!" {:type :exploud.http/connect-timeout})))

(fact "that an instance is unhealthy when a socket timeout exception is thrown"
      (instance-healthy? ..instance.. "port" "healthcheck")
      => false
      (provided
       (instance-ip ..instance..)
       => "ip"
       (http/simple-get "http://ip:port/healthcheck" {:socket-timeout 2000})
       =throws=> (ex-info "Whoops!" {:type :exploud.http/socket-timeout})))

(fact "that when checking ASG health no instances returns true if none are wanted"
      (asg-healthy? "environment" "region" "asg" 0 8080 "healthcheck")
      => true
      (provided
       (asgard/instances-in-asg "environment" "region" "asg")
       => []))

(fact "that when checking ASG health no instances returns false if any are wanted"
      (asg-healthy? "environment" "region" "asg" 1 8080 "healthcheck")
      => false
      (provided
       (asgard/instances-in-asg "environment" "region" "asg")
       => []))

(fact "that when checking ASG health all healthy instances returns true"
      (asg-healthy? "environment" "region" "asg" 2 8080 "healthcheck")
      => true
      (provided
       (asgard/instances-in-asg "environment" "region" "asg")
       => [{:instance {:privateIpAddress "100.100.100.101"}}
           {:instance {:privateIpAddress "100.100.100.102"}}]
       (http/simple-get "http://100.100.100.101:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}
       (http/simple-get "http://100.100.100.102:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}))

(fact "that when checking ASG health no healthy instances returns false"
      (asg-healthy? "environment" "region" "asg" 2 8080 "healthcheck")
      => false
      (provided
       (asgard/instances-in-asg "environment" "region" "asg")
       => [{:instance {:privateIpAddress "100.100.100.101"}}
           {:instance {:privateIpAddress "100.100.100.102"}}]
       (http/simple-get "http://100.100.100.101:8080/healthcheck" {:socket-timeout 2000})
       => {:status 500}
       (http/simple-get "http://100.100.100.102:8080/healthcheck" {:socket-timeout 2000})
       => {:status 500}))

(fact "that when checking ASG health one unhealthy instance returns false"
      (asg-healthy? "environment" "region" "asg" 2 8080 "healthcheck")
      => false
      (provided
       (asgard/instances-in-asg "environment" "region" "asg")
       => [{:instance {:privateIpAddress "100.100.100.101"}}
           {:instance {:privateIpAddress "100.100.100.102"}}]
       (http/simple-get "http://100.100.100.101:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}
       (http/simple-get "http://100.100.100.102:8080/healthcheck" {:socket-timeout 2000})
       => {:status 500}))

(fact "that checking ASG health does the right things when unhealthy"
      (check-asg-health "environment" "region" "asg" 2 8080 "healthcheck" ..deploy-id.. {:log []} ..completed.. ..timed-out.. 5)
      => ..reschedule-result..
      (provided
       (asg-healthy? "environment" "region" "asg" 2 8080 "healthcheck")
       => false
       (time/now)
       => ..now..
       (store/store-task ..deploy-id.. {:log [{:message "Checking healthcheck on port 8080 and path /healthcheck."
                                               :date ..now..}]
                                        :status "running"})
       => ..store-result..
       (schedule-asg-check "environment" "region" "asg" 2 8080 "healthcheck" ..deploy-id.. {:log [{:message "Checking healthcheck on port 8080 and path /healthcheck."
                                                                                     :date ..now..}]
                                                                              :status "running"} ..completed.. ..timed-out.. 4)
       => ..reschedule-result..))

(fact "that when checking ELB health no instances returns true"
      (elb-healthy? "environment" "region" "elb" "asg")
      => true
      (provided
       (asgard/load-balancer "environment" "region" "elb")
       => {:instanceStates []}))

(fact "that when checking ELB health no instances in given asg returns true"
      (elb-healthy? "environment" "region" "elb" "asg")
      => true
      (provided
       (asgard/load-balancer "environment" "region" "elb")
       => {:instanceStates [{:autoScalingGroupName "other"
                             :state "Unhealthy"}
                            {:autoScalingGroupName "other"
                             :state "InService"}]}))

(fact "that when checking ELB health all healthy instances returns true"
      (elb-healthy? "environment" "region" "elb" "asg")
      => true
      (provided
       (asgard/load-balancer "environment" "region" "elb")
       => {:instanceStates [{:autoScalingGroupName "asg"
                             :state "InService"}
                            {:autoScalingGroupName "asg"
                             :state "InService"}]}))

(fact "that when checking ELB health one unhealthy instance in ASG returns false"
      (elb-healthy? "environment" "region" "elb" "asg")
      => false
      (provided
       (asgard/load-balancer "environment" "region" "elb")
       => {:instanceStates [{:autoScalingGroupName "asg"
                             :state "InService"}
                            {:autoScalingGroupName "asg"
                             :state "Busted"}]}))

(fact "that when checking ELB health one unhealthy instance outside the ASG returns true"
      (elb-healthy? "environment" "region" "elb" "asg")
      => true
      (provided
       (asgard/load-balancer "environment" "region" "elb")
       => {:instanceStates [{:autoScalingGroupName "another-asg"
                             :state "Busted"}
                            {:autoScalingGroupName "asg"
                             :state "InService"}]}))

(fact "that checking ELB health does the right things when unhealthy"
      (check-elb-health "environment" "region" "elb" "asg" ..deploy-id.. {:log []} ..completed.. ..timed-out.. 5)
      => ..reschedule-result..
      (provided
       (elb-healthy? "environment" "region" "elb" "asg")
       => false
       (time/now)
       => ..now..
       (store/store-task ..deploy-id.. {:log [{:message "Checking ELB (elb) health."
                                               :date ..now..}]
                                        :status "running"})
       => ..store-result..
       (schedule-elb-check "environment" "region" ["elb"] "asg" ..deploy-id.. {:log [{:message "Checking ELB (elb) health."
                                                                                      :date ..now..}]
                                                                               :status "running"} ..completed.. ..timed-out.. 4)
       => ..reschedule-result..))

(fact "that checking ELB health does the right things when unhealthy and checking multiple ELBs"
      (check-elb-health "environment" "region" ["elb1" "elb2"] "asg" ..deploy-id.. {:log []} ..completed.. ..timed-out.. 5)
      => ..reschedule-result..
      (provided
       (elb-healthy? "environment" "region" "elb1" "asg")
       => false
       (time/now)
       => ..now..
       (store/store-task ..deploy-id.. {:log [{:message "Checking ELB (elb1) health."
                                               :date ..now..}]
                                        :status "running"})
       => ..store-result..
       (schedule-elb-check "environment" "region" ["elb1" "elb2"] "asg" ..deploy-id.. {:log [{:message "Checking ELB (elb1) health."
                                                                                              :date ..now..}]
                                                                                       :status "running"} ..completed.. ..timed-out.. 4)
       => ..reschedule-result..))

(fact "that checking ELB health does the right things when healthy and checking multiple ELBs"
      (check-elb-health "environment" "region" ["elb1" "elb2"] "asg" ..deploy-id.. {:log []} ..completed.. ..timed-out.. 5)
      => ..reschedule-result..
      (provided
       (elb-healthy? "environment" "region" "elb1" "asg")
       => true
       (time/now)
       => ..now..
       (store/store-task ..deploy-id.. {:log [{:message "Checking ELB (elb1) health."
                                               :date ..now..}]
                                        :status "running"})
       => ..store-result..
       (schedule-elb-check "environment" "region" ["elb2"] "asg" ..deploy-id.. {:log [{:message "Checking ELB (elb1) health."
                                                                                       :date ..now..}]
                                                                                :status "running"} ..completed.. ..timed-out.. 4)
       => ..reschedule-result..))

(fact "that checking ELB health does the right things when we're all out of ELBs to check"
      (check-elb-health "environment" "region" [] "asg" ..deploy-id.. {:log []} (fn [_ _] ..completed-result..) ..timed-out.. 5)
      => ..completed-result..)
