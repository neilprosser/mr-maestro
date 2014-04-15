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
      (check-instance-health "ip" "port" "healthcheck")
      => (contains {:successful? false})
      (provided
       (http/simple-get "http://ip:port/healthcheck" {:socket-timeout 2000})
       =throws=> (ex-info "Whoops!" {:type :exploud.http/connect-timeout})))

(fact "that an instance is unhealthy when a socket timeout exception is thrown"
      (check-instance-health "ip" "port" "healthcheck")
      => (contains {:successful? false})
      (provided
       (http/simple-get "http://ip:port/healthcheck" {:socket-timeout 2000})
       =throws=> (ex-info "Whoops!" {:type :exploud.http/socket-timeout})))

(fact "that when checking the health of an instance we use the direct IP to get hold of the information."
      (check-instance-health "10.123.71.23" "port" "healthcheck")
      => (contains {:successful? true})
      (provided
       (http/simple-get "http://10.123.71.23:port/healthcheck" {:socket-timeout 2000})
       => {:status 200}))

(fact "that when checking ASG health no instances returns true if none are wanted"
      (determine-asg-health "environment" "region" "asg" 0 8080 "healthcheck")
      => (contains {:healthy? true})
      (provided
       (asgard/instances-in-asg "environment" "region" "asg")
       => []))

(fact "that when checking ASG health no instances returns false if any are wanted"
      (determine-asg-health "environment" "region" "asg" 1 8080 "healthcheck")
      => (contains {:healthy? false})
      (provided
       (asgard/instances-in-asg "environment" "region" "asg")
       => []))

(fact "that when checking ASG health all healthy instances returns true"
      (determine-asg-health "environment" "region" "asg" 2 8080 "healthcheck")
      => (contains {:healthy? true})
      (provided
       (asgard/instances-in-asg "environment" "region" "asg")
       => [{:instance {:privateIpAddress "100.100.100.101"}}
           {:instance {:privateIpAddress "100.100.100.102"}}]
       (http/simple-get "http://100.100.100.101:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}
       (http/simple-get "http://100.100.100.102:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}))

(fact "that when checking ASG health all healthy instances returns true"
      (determine-asg-health "environment" "region" "asg" 2 8080 "healthcheck")
      => (contains {:healthy? true})
      (provided
       (asgard/instances-in-asg "environment" "region" "asg")
       => [{:instance {:privateIpAddress "100.100.100.101"}}
           {:instance {:privateIpAddress "100.100.100.102"}}]
       (http/simple-get "http://100.100.100.101:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}
       (http/simple-get "http://100.100.100.102:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}))

(fact "that when checking ASG health `min` healthy instances returns true"
      (determine-asg-health "environment" "region" "asg" 2 8080 "healthcheck")
      => (contains {:healthy? true})
      (provided
       (asgard/instances-in-asg "environment" "region" "asg")
       => [{:instance {:privateIpAddress "100.100.100.101"}}
           {:instance {:privateIpAddress "100.100.100.102"}}
           {:instance {:privateIpAddress "100.100.100.103"}}]
       (http/simple-get "http://100.100.100.101:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}
       (http/simple-get "http://100.100.100.102:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}
       (http/simple-get "http://100.100.100.103:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}))

(fact "that when checking ASG health minimum healthy instances returns true"
      (determine-asg-health "environment" "region" "asg" 2 8080 "healthcheck")
      => (contains {:healthy? true})
      (provided
       (asgard/instances-in-asg "environment" "region" "asg")
       => [{:instance {:privateIpAddress "100.100.100.101"}}
           {:instance {:privateIpAddress "100.100.100.102"}}
           {:instance {:privateIpAddress "100.100.100.103"}}
           {:instance {:privateIpAddress "100.100.100.104"}}]
       (http/simple-get "http://100.100.100.101:8080/healthcheck" {:socket-timeout 2000})
       => {:status 500}
       (http/simple-get "http://100.100.100.102:8080/healthcheck" {:socket-timeout 2000})
       => {:status 500}
       (http/simple-get "http://100.100.100.103:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}
       (http/simple-get "http://100.100.100.104:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}))

(fact "that when checking ASG health at least minimum healthy instances returns true"
      (determine-asg-health "environment" "region" "asg" 2 8080 "healthcheck")
      => (contains {:healthy? true})
      (provided
       (asgard/instances-in-asg "environment" "region" "asg")
       => [{:instance {:privateIpAddress "100.100.100.101"}}
           {:instance {:privateIpAddress "100.100.100.102"}}
           {:instance {:privateIpAddress "100.100.100.103"}}
           {:instance {:privateIpAddress "100.100.100.104"}}]
       (http/simple-get "http://100.100.100.101:8080/healthcheck" {:socket-timeout 2000})
       => {:status 500}
       (http/simple-get "http://100.100.100.102:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}
       (http/simple-get "http://100.100.100.103:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}
       (http/simple-get "http://100.100.100.104:8080/healthcheck" {:socket-timeout 2000})
       => {:status 200}))

(fact "that when checking ASG health exception on reading ASG returns false"
      (determine-asg-health "environment" "region" "asg" 1 8080 "healthcheck")
      => (contains {:healthy? false})
      (provided
       (asgard/instances-in-asg "environment" "region" "asg")
       =throws=> (Exception.)))

(fact "that checking ASG health does the right things when unhealthy"
      (check-asg-health "environment" "region" "asg" 2 8080 "healthcheck" ..deploy-id.. {:log []} ..completed.. ..timed-out.. 5)
      => ..reschedule-result..
      (provided
       (determine-asg-health "environment" "region" "asg" 2 8080 "healthcheck")
       => {:healthy? false :results [{:successful? false :url "http://somewhere:8080/healthcheck"}]}
       (time/now)
       => ..now..
       (store/store-task ..deploy-id.. {:log [{:message "Healthcheck at http://somewhere:8080/healthcheck - false"
                                               :date ..now..}]
                                        :status "running"})
       => ..store-result..
       (schedule-asg-check "environment" "region" "asg" 2 8080 "healthcheck" ..deploy-id.. {:log [{:message "Healthcheck at http://somewhere:8080/healthcheck - false"
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
