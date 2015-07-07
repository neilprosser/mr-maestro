(ns maestro.policies-test
  (:require [amazonica.aws.autoscaling :as autoscaling]
            [bouncer.core :as b]
            [maestro
             [aws :as aws]
             [policies :refer :all]
             [util :as util]]
            [midje.sweet :refer :all]))

(fact "that validating a policy kicks off at the right time"
      (first (b/validate {:adjustment-type "whatever"} policy-validators)) =not=> nil
      (first (b/validate {:adjustment-type "ExactCapacity"} policy-validators)) => nil)

(fact "that we can create a policy"
      (create-policy "environment" "region" {:policy-name "policy-1"}) => ["policy-1" "policy-1-arn"]
      (provided
       (util/to-params {:policy-name "policy-1"}) => [:policy-1 "params"]
       (autoscaling/put-scaling-policy {:endpoint "region"} :policy-1 "params") => {:policy-arn "policy-1-arn"}))

(fact "that creating policies return the ARN for each policy created"
      (create-policies "environment" "region" [..policy-1.. ..policy-2..]) => {"policy-1" ..policy-1-arn.. "policy-2" ..policy-2-arn..}
      (provided
       (create-policy "environment" "region" ..policy-1..) => ["policy-1" ..policy-1-arn..]
       (create-policy "environment" "region" ..policy-2..) => ["policy-2" ..policy-2-arn..]))

(fact "that getting policies for an auto scaling group works"
      (policies-for-auto-scaling-group "environment" "region" "asg") => ..policies..
      (provided
       (aws/config "environment" "region") => ..config..
       (autoscaling/describe-policies ..config.. :auto-scaling-group-name "asg") => {:scaling-policies ..policies..}))
