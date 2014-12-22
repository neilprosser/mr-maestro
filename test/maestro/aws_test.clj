(ns maestro.aws-test
  (:require [amazonica.aws
             [ec2 :as ec2]
             [securitytoken :as sts]]
            [cheshire.core :as json]
            [environ.core :refer :all]
            [maestro
             [aws :refer :all]
             [environments :as environments]
             [identity :as id]
             [numel :as numel]]
            [midje.sweet :refer :all]))

(fact "that we don't provide alternative credentials when using the current account ID"
      (alternative-credentials-if-necessary "env") => nil
      (provided
       (environments/account-id "env") => "id"
       (id/current-account-id) => "id"
       (sts/assume-role {:role-arn "arn:aws:iam::id:role/maestro"
                         :role-session-name "maestro"}) => nil :times 0))

(fact "that we provide alternative credentials when not using the current account ID"
      (alternative-credentials-if-necessary "env") => ..credentials..
      (provided
       (environments/account-id "env") => "other-id"
       (id/current-account-id) => "id"
       (sts/assume-role {:role-arn "arn:aws:iam::other-id:role/maestro"
                         :role-session-name "maestro"}) => {:credentials ..credentials..}))

(fact "that config is generated correctly for `poke`"
      (config :poke "region") => {:potential :alternative
                                  :endpoint "region"}
      (provided
       (alternative-credentials-if-necessary :poke) => {:potential :alternative}))

(fact "that getting the autoscaling topic works"
      (autoscaling-topic "env") => "topic-arn"
      (provided
       (environments/autoscaling-topic "env") => "topic-arn"))

(fact "that getting the announcement queue URL works"
      (announcement-queue-url "region" "env") => "https://region.queue.amazonaws.com/account-id/autoscale-announcements"
      (provided
       (environments/account-id "env") => "account-id"))

(fact "that the auto scaling group creation message is correct"
      (asg-created-message "asg") => ..whole-message..
      (provided
       (json/generate-string {:Event "autoscaling:ASG_LAUNCH" :AutoScalingGroupName "asg"}) => ..message..
       (json/generate-string {:Message ..message..}) => ..whole-message..))

(fact "that the auto scaling group deletion message is correct"
      (asg-deleted-message "asg") => ..whole-message..
      (provided
       (json/generate-string {:Event "autoscaling:ASG_TERMINATE" :AutoScalingGroupName "asg"}) => ..message..
       (json/generate-string {:Message ..message..}) => ..whole-message..))

(fact "that describe-instances is called with name, environment and state"
      (describe-instances "env" "region" "name" "state") => truthy
      (provided
       (config anything anything) => {}
       (ec2/describe-instances anything
                               :filters
                               [{:name "tag:Name" :values ["name-env-*"]}
                                {:name "instance-state-name" :values ["state"]}])
       => [{}]
       (numel/application-registrations "env" "name") => {}))

(fact "that describe-instances-plain formats response for multiple reservations"
      (describe-instances-plain "env" "region" nil nil) => (contains "two")
      (provided
       (config anything anything) => {}
       (ec2/describe-instances anything
                               anything
                               anything) => {:reservations [{:instances [..instance1..]}
                                                            {:instances [..instance2..]}]}
                               (transform-instance-description ..instance1..) => {:name "one"}
                               (transform-instance-description ..instance2..) => {:name "two"}))

(fact "that describe-instances-plain formats response from multiple instances in one reservation"
      (describe-instances-plain "env" "region" nil nil) => (contains "two")
      (provided
       (config anything anything) => {}
       (ec2/describe-instances anything
                               anything
                               anything) => {:reservations [{:instances [..instance1.. ..instance2..]}]}
                               (transform-instance-description ..instance1..) => {:name "one"}
                               (transform-instance-description ..instance2..) => {:name "two"}))

(fact "that transform-instance-description returns a transformed description"
      (transform-instance-description
       {:tags [{:key "Name" :value ..name..}]
        :instance-id ..instance..
        :image-id ..image..
        :private-ip-address ..ip..
        :launch-time ..launch-time..})
      => {:name ..name.. :instance-id ..instance.. :image-id ..image.. :private-ip ..ip.. :launch-time ..launch-time..})

(fact "that transform-instance-description handles missing Name tag"
      (transform-instance-description
       {:tags []
        :instance-id ..instance..
        :image-id ..image..
        :private-ip-address ..ip..
        :launch-time ..launch-time..})
      => {:name "none" :instance-id ..instance.. :image-id ..image.. :private-ip ..ip.. :launch-time ..launch-time..})

(fact "that getting the last auto scaling group for an application works"
      (last-application-auto-scaling-group "search" "poke" "eu-west-1") => {:auto-scaling-group-name "search-poke-v023"}
      (provided
       (auto-scaling-groups "poke" "eu-west-1") => [{:auto-scaling-group-name "app1-something-v012"}
                                                    {:auto-scaling-group-name "search-poke"}
                                                    {:auto-scaling-group-name "search-poke-v023"}
                                                    {:auto-scaling-group-name "search-poke-v000"}
                                                    {:auto-scaling-group-name "app2-poke-v000"}]))

(fact "that getting the last auto scaling group for an application works when it's not got the v000 bit"
      (last-application-auto-scaling-group "search" "poke" "eu-west-1") => {:auto-scaling-group-name "search-poke"}
      (provided
       (auto-scaling-groups "poke" "eu-west-1") => [{:auto-scaling-group-name "app1-something-v012"}
                                                    {:auto-scaling-group-name "search-poke"}
                                                    {:auto-scaling-group-name "app2-poke-v000"}]))

(fact "that getting the subnets for a specific purpose handles no subnets coming back"
      (subnets-by-purpose "environment" "region" "purpose")
      => nil
      (provided
       (config anything anything) => {}
       (ec2/describe-subnets anything) => {:subnets []}))

(fact "that getting the subnets for a specific purpose gracefully handles a subnet missing the `immutable_metadata` tag"
      (subnets-by-purpose "environment" "region" "purpose")
      => nil
      (provided
       (config anything anything) => {}
       (ec2/describe-subnets anything) => {:subnets [{:tags [{:key "something" :value "whatever"}]}]}))

(fact "that getting the subnets for a specific purpose gracefully handles a subnet with invalid JSON in the `immutable_metadata` tag"
      (subnets-by-purpose "environment" "region" "purpose")
      => nil
      (provided
       (config anything anything) => {}
       (ec2/describe-subnets anything) => {:subnets [{:tags [{:key "immutable_metadata" :value "garbage"}]}]}))

(fact "that filtering subnets by availability zone works when something is specified"
      (filter-by-availability-zones ["eu-west-1b"] [{:subnet-id "1" :availability-zone "eu-west-1a"} {:subnet-id "2" :availability-zone "eu-west-1b"}])
      => [{:subnet-id "2" :availability-zone "eu-west-1b"}])

(fact "that filtering subnets by availability zone gives back everything when nothing is specified"
      (filter-by-availability-zones [] [{:subnet-id "1" :availability-zone "eu-west-1a"} {:subnet-id "2" :availability-zone "eu-west-1b"}])
      => [{:subnet-id "1" :availability-zone "eu-west-1a"} {:subnet-id "2" :availability-zone "eu-west-1b"}])

(fact "that filtering subnets by availability zone gives back everything when nil is specified"
      (filter-by-availability-zones nil [{:subnet-id "1" :availability-zone "eu-west-1a"} {:subnet-id "2" :availability-zone "eu-west-1b"}])
      => [{:subnet-id "1" :availability-zone "eu-west-1a"} {:subnet-id "2" :availability-zone "eu-west-1b"}])
