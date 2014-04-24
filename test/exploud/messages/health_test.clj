(ns exploud.messages.health-test
  (:require [exploud
             [aws :as aws]
             [http :as http]]
            [exploud.messages.health :refer :all]
            [midje.sweet :refer :all]))

(def wait-for-instances-to-be-healthy-params
  {:environment "environment"
   :region "region"
   :new-state {
               :auto-scaling-group-name "asg"
               :tyranitar {:application-properties {:service.port 9090
                                                    :service.healthcheck.path "/the/healthcheck"
                                                    :service.healthcheck.skip false}
                           :deployment-params {:min 2}}}})

(fact "that healthchecks are skipped if told to"
      (wait-for-instances-to-be-healthy {:attempt 1 :parameters (assoc-in wait-for-instances-to-be-healthy-params [:new-state :tyranitar :application-properties :service.healthcheck.skip] true)}) => (contains {:status :success}))

(fact "that checking the health when min is zero skips the process"
      (wait-for-instances-to-be-healthy {:attempt 1 :parameters (assoc-in wait-for-instances-to-be-healthy-params [:new-state :tyranitar :deployment-params :min] 0)}) => (contains {:status :success}))

(fact "that getting no instances back when min is greater than zero retries"
      (wait-for-instances-to-be-healthy {:attempt 1 :parameters wait-for-instances-to-be-healthy-params}) => {:status :retry
                                                                                                              :backoff-ms 10000}
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => []))

(fact "that getting one healthy instance when the minimum is two retries"
      (wait-for-instances-to-be-healthy {:attempt 1 :parameters wait-for-instances-to-be-healthy-params}) => {:status :retry
                                                                                                              :backoff-ms 10000}
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => [{:instance-id "i-1"}
                                                                           {:instance-id "i-2"}]
       (aws/instances "environment" "region" ["i-1" "i-2"]) => [{:instance-id "i-1"
                                                                 :private-ip-address "ip1"}
                                                                {:instance-id "i-2"
                                                                 :private-ip-address "ip2"}]
       (http/simple-get "http://ip1:9090/the/healthcheck" {:socket-timeout 2000}) => {:status 500}
       (http/simple-get "http://ip2:9090/the/healthcheck" {:socket-timeout 2000}) => {:status 200}))

(fact "that getting two healthy instances when the minimum is two succeeds"
      (wait-for-instances-to-be-healthy {:attempt 1 :parameters wait-for-instances-to-be-healthy-params}) => (contains {:status :success})
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => [{:instance-id "i-1"}
                                                                           {:instance-id "i-2"}]
       (aws/instances "environment" "region" ["i-1" "i-2"]) => [{:instance-id "i-1"
                                                                 :private-ip-address "ip1"}
                                                                {:instance-id "i-2"
                                                                 :private-ip-address "ip2"}]
       (http/simple-get "http://ip1:9090/the/healthcheck" {:socket-timeout 2000}) => {:status 200}
       (http/simple-get "http://ip2:9090/the/healthcheck" {:socket-timeout 2000}) => {:status 200}))

(fact "that getting an exception while attempting the healthcheck counts as a failed check"
      (wait-for-instances-to-be-healthy {:attempt 1 :parameters wait-for-instances-to-be-healthy-params}) => {:status :retry
                                                                                                              :backoff-ms 10000}
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => [{:instance-id "i-1"}
                                                                           {:instance-id "i-2"}]
       (aws/instances "environment" "region" ["i-1" "i-2"]) => [{:instance-id "i-1"
                                                                 :private-ip-address "ip1"}
                                                                {:instance-id "i-2"
                                                                 :private-ip-address "ip2"}]
       (http/simple-get "http://ip1:9090/the/healthcheck" {:socket-timeout 2000}) => {:status 200}
       (http/simple-get "http://ip2:9090/the/healthcheck" {:socket-timeout 2000}) =throws=> (ex-info "Busted" {})))

(fact "that getting an exception while attempting to retrieve the instance details is an error"
      (wait-for-instances-to-be-healthy {:attempt 1 :parameters wait-for-instances-to-be-healthy-params}) => (contains {:status :error})
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") =throws=> (ex-info "Busted" {})))

(fact "that after 100 attempts we give up checking health and getting no instances"
      (wait-for-instances-to-be-healthy {:attempt 50 :parameters wait-for-instances-to-be-healthy-params}) => (contains {:status :error})
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => []))

(fact "that after 100 attempts we give up checking health and getting unsuccessful responses"
      (wait-for-instances-to-be-healthy {:attempt 50 :parameters wait-for-instances-to-be-healthy-params}) => (contains {:status :error})
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => [{:instance-id "i-1"}
                                                                           {:instance-id "i-2"}]
       (aws/instances "environment" "region" ["i-1" "i-2"]) => [{:instance-id "i-1"
                                                                 :private-ip-address "ip1"}
                                                                {:instance-id "i-2"
                                                                 :private-ip-address "ip2"}]
       (http/simple-get "http://ip1:9090/the/healthcheck" {:socket-timeout 2000}) => {:status 500}
       (http/simple-get "http://ip2:9090/the/healthcheck" {:socket-timeout 2000}) => {:status 200}))

(def wait-for-load-balancers-to-be-healthy-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"
               :tyranitar {:deployment-params {:selected-load-balancers ["lb-1" "lb-2"]}}}})

(fact "that waiting for load balancer health of no load balancers skips it"
      (wait-for-load-balancers-to-be-healthy {:attempt 1
                                              :parameters (assoc-in wait-for-load-balancers-to-be-healthy-params [:new-state :tyranitar :deployment-params :selected-load-balancers] [])}) => (contains {:status :success}))

(fact "that waiting for load balancer health of auto scaling group with no instances is successful"
      (wait-for-load-balancers-to-be-healthy {:attempt 1
                                              :parameters wait-for-load-balancers-to-be-healthy-params}) => (contains {:status :success})
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => []))

(fact "that waiting for load balancer health of auto scaling group with no healthy instances retries"
      (wait-for-load-balancers-to-be-healthy {:attempt 1
                                              :parameters wait-for-load-balancers-to-be-healthy-params}) => {:status :retry
                      :backoff-ms 10000}
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => [{:instance-id "id-1"}]
       (aws/load-balancer-health "environment" "region" "lb-1") => [{:instance-id "id-1" :state "NotInService"}]
       (aws/load-balancer-health "environment" "region" "lb-2") => [{:instance-id "id-1" :state "InService"}]))

(fact "that waiting for load balancer health of auto scaling group with not enough healthy instances retries"
      (wait-for-load-balancers-to-be-healthy {:attempt 1
                                              :parameters wait-for-load-balancers-to-be-healthy-params}) => {:status :retry
                                                                                                             :backoff-ms 10000}
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => [{:instance-id "id-1"} {:instance-id "id-2"}]
       (aws/load-balancer-health "environment" "region" "lb-1") => [{:instance-id "id-1" :state "NotInService"}
                                                                    {:instance-id "id-2" :state "InService"}]
       (aws/load-balancer-health "environment" "region" "lb-2") => [{:instance-id "id-1" :state "InService"}
                                                                    {:instance-id "id-2" :state "InService"}]))

(fact "that waiting for load balancer health of auto scaling group with unknown unhealthy instances succeeds"
      (wait-for-load-balancers-to-be-healthy {:attempt 1
                                              :parameters wait-for-load-balancers-to-be-healthy-params}) => (contains {:status :success})
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => [{:instance-id "id-1"}]
       (aws/load-balancer-health "environment" "region" "lb-1") => [{:instance-id "id-1" :state "InService"}
                                                                    {:instance-id "id-2" :state "NotInService"}]
       (aws/load-balancer-health "environment" "region" "lb-2") => [{:instance-id "id-1" :state "InService"}
                                                                    {:instance-id "id-2" :state "InService"}]))

(fact "that an exception while waiting for load balancer health is an error"
      (wait-for-load-balancers-to-be-healthy {:attempt 1
                                              :parameters wait-for-load-balancers-to-be-healthy-params}) => (contains {:status :error})
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") =throws=> (ex-info "Busted" {})))

(fact "that waiting for load balancer health errors if too many unsuccessful attempts have been made"
      (wait-for-load-balancers-to-be-healthy {:attempt 50
                                              :parameters wait-for-load-balancers-to-be-healthy-params}) => (contains {:status :error})
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => [{:instance-id "id-1"} {:instance-id "id-2"}]
       (aws/load-balancer-health "environment" "region" "lb-1") => [{:instance-id "id-1" :state "NotInService"}
                                                                    {:instance-id "id-2" :state "InService"}]
       (aws/load-balancer-health "environment" "region" "lb-2") => [{:instance-id "id-1" :state "InService"}
                                                                    {:instance-id "id-2" :state "InService"}]))