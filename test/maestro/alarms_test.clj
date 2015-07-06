(ns maestro.alarms-test
  (:require [amazonica.aws.cloudwatch :as cw]
            [bouncer
             [core :as b]]
            [maestro
             [alarms :refer :all]
             [aws :as aws]
             [environments :as environments]]
            [midje.sweet :refer :all]))

(fact "that our CPU credit balance low alarm definition is correct"
      (cpu-credit-balance-low-alarm "jagus-prod-v030" "arn:aws:sns:eu-west-1:269544559808:slack-messages" 50)
      => {:actions-enabled true
          :alarm-actions ["arn:aws:sns:eu-west-1:269544559808:slack-messages"]
          :alarm-description "This alarm indicates that the credit balance (which governs the speed t2-class instances can run at) is beginning to run out. If these credits run out, that instance will start to throttle its processor."
          :alarm-name "jagus-prod-v030-cpu-credit-balance-low"
          :comparison-operator "LessThanOrEqualToThreshold"
          :dimensions [{:name "AutoScalingGroupName"
                        :value "jagus-prod-v030"}]
          :evaluation-periods 1
          :insufficient-data-actions ["arn:aws:sns:eu-west-1:269544559808:slack-messages"]
          :metric-name "CPUCreditBalance"
          :namespace "AWS/EC2"
          :ok-actions ["arn:aws:sns:eu-west-1:269544559808:slack-messages"]
          :period 300
          :statistic "Minimum"
          :threshold 50})

(fact "that our CPU credit balance low alarm definition is nil if we don't provide things we need"
      (cpu-credit-balance-low-alarm nil "topic" 30) => nil
      (cpu-credit-balance-low-alarm "name" nil 30) => nil
      (cpu-credit-balance-low-alarm "name" "topic" nil) => nil)

(fact "that we can retrieve the topic ARN for an environment"
      (topic-arn-for ..environment..) => ..topic-arn..
      (provided
       (environments/alert-topic ..environment..) => ..topic-arn..))

(fact "that we can get the threshold for an instance type"
      (threshold-for "t2.micro") => 15
      (threshold-for "m1.small") => nil)

(fact "that our standard alarms doesn't return any nil entries"
      (standard-alarms ..environment.. ..region.. {}) => []
      (provided
       (topic-arn-for ..environment..) => nil
       (threshold-for anything) => nil))

(fact "that our standard alarms is a collection of various alarms"
      (standard-alarms ..environment.. ..region.. {:auto-scaling-group-name ..auto-scaling-group-name..
                                                   :tyranitar {:deployment-params {:instance-type ..instance-type..}}}) => [..cpu-credit-balance-low..]
      (provided
       (topic-arn-for ..environment..) => ..topic-arn..
       (threshold-for ..instance-type..) => ..threshold..
       (cpu-credit-balance-low-alarm ..auto-scaling-group-name.. ..topic-arn.. ..threshold..) => ..cpu-credit-balance-low..))

(fact "that we can retrieve the alarms for an auto-scaling group"
      (alarms-for-auto-scaling-group "environment" "region" "group-name") => ..alarms..
      (provided
       (aws/config "environment" "region") => ..config..
       (cw/describe-alarms ..config.. :alarm-name-prefix "group-name-") => {:metric-alarms ..alarms..}))

(fact "that validating an alarm works"
      (first (b/validate {:comparison-operator "whatever"} alarm-validators)) =not=> nil
      (first (b/validate {:statistic "whatever"} alarm-validators)) =not=> nil
      (first (b/validate {:unit "whatever"} alarm-validators)) =not=> nil
      (first (b/validate {:comparison-operator "GreaterThanThreshold"
                          :statistic "Average"
                          :unit "Bytes"} alarm-validators)) => nil)
