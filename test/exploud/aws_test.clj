(ns exploud.aws-test
  (:require [amazonica.aws.ec2 :as ec2]
            [cheshire.core :as json]
            [environ.core :refer :all]
            [exploud
             [aws :refer :all]
             [numel :as numel]]
            [midje.sweet :refer :all]))

(fact "ec2/describe-instances is called with name and state"
      (describe-instances "env" "region" "name" "state") => truthy
      (provided
       (ec2/describe-instances anything
                               :filters
                               [{:name "tag:Name" :values ["name-*"]}
                                {:name "instance-state-name" :values ["state"]}])
       => [{}]
       (numel/application-registrations "env" "name") => {}))

(fact "ec2/describe-instances defaults name and state if nil"
      (describe-instances "env" "region" nil nil) => truthy
      (provided
       (ec2/describe-instances anything
                               :filters
                               [{:name "tag:Name" :values ["*"]}
                                {:name "instance-state-name" :values ["running"]}])
       => [{}]
       (numel/application-registrations "env" anything) => {} :times 0))

(fact "ec2/describe-instances preserves name if contains *"
      (describe-instances "env" "region" "part-*-part" nil) => truthy
      (provided
       (ec2/describe-instances anything
                               :filters
                               [{:name "tag:Name" :values ["part-*-part"]}
                                {:name "instance-state-name" :values ["running"]}])
       => [{}]
       (numel/application-registrations "env" "part-*-part") => {}))

(fact "describe instances plain formats response for multiple reservations"
      (describe-instances-plain "env" "region" nil nil) => (contains "two")
      (provided
       (ec2/describe-instances anything
                               anything
                               anything) => {:reservations [{:instances [..instance1..]}
                                                            {:instances [..instance2..]}]}
                               (transform-instance-description ..instance1..) => {:name "one"}
                               (transform-instance-description ..instance2..) => {:name "two"}))

(fact "describe instances plain formats response from multiple instances in one reservation"
      (describe-instances-plain "env" "region" nil nil) => (contains "two")
      (provided
       (ec2/describe-instances anything
                               anything
                               anything) => {:reservations [{:instances [..instance1.. ..instance2..]}]}
                               (transform-instance-description ..instance1..) => {:name "one"}
                               (transform-instance-description ..instance2..) => {:name "two"}))

(fact "transform-instance-description returns a transformed description"
      (transform-instance-description
       {:tags [{:key "Name" :value ..name..}]
        :instance-id ..instance..
        :image-id ..image..
        :private-ip-address ..ip..
        :launch-time ..launch-time..})
      => {:name ..name.. :instance-id ..instance.. :image-id ..image.. :private-ip ..ip.. :launch-time ..launch-time..})

(fact "transform-instance-description handles missing Name tag"
      (transform-instance-description
       {:tags []
        :instance-id ..instance..
        :image-id ..image..
        :private-ip-address ..ip..
        :launch-time ..launch-time..})
      => {:name "none" :instance-id ..instance.. :image-id ..image.. :private-ip ..ip.. :launch-time ..launch-time..})

(fact "getting the last auto scaling group for an application works"
      (last-application-auto-scaling-group "search" "poke" "eu-west-1") => {:auto-scaling-group-name "search-poke-v023"}
      (provided
       (auto-scaling-groups "poke" "eu-west-1") => [{:auto-scaling-group-name "app1-something-v012"}
                                                    {:auto-scaling-group-name "search-poke"}
                                                    {:auto-scaling-group-name "search-poke-v023"}
                                                    {:auto-scaling-group-name "search-poke-v000"}
                                                    {:auto-scaling-group-name "app2-poke-v000"}]))

(fact "getting the last auto scaling group for an application works when it's not got the v000 bit"
      (last-application-auto-scaling-group "search" "poke" "eu-west-1") => {:auto-scaling-group-name "search-poke"}
      (provided
       (auto-scaling-groups "poke" "eu-west-1") => [{:auto-scaling-group-name "app1-something-v012"}
                                                    {:auto-scaling-group-name "search-poke"}
                                                    {:auto-scaling-group-name "app2-poke-v000"}]))

(fact "that filtering subnets by availability zone works when something is specified"
      (filter-by-availability-zones ["eu-west-1b"] [{:subnet-id "1" :availability-zone "eu-west-1a"} {:subnet-id "2" :availability-zone "eu-west-1b"}])
      => [{:subnet-id "2" :availability-zone "eu-west-1b"}])

(fact "that filtering subnets by availability zone gives back everything when nothing is specified"
      (filter-by-availability-zones [] [{:subnet-id "1" :availability-zone "eu-west-1a"} {:subnet-id "2" :availability-zone "eu-west-1b"}])
      => [{:subnet-id "1" :availability-zone "eu-west-1a"} {:subnet-id "2" :availability-zone "eu-west-1b"}])

(fact "that filtering subnets by availability zone gives back everything when nil is specified"
      (filter-by-availability-zones nil [{:subnet-id "1" :availability-zone "eu-west-1a"} {:subnet-id "2" :availability-zone "eu-west-1b"}])
      => [{:subnet-id "1" :availability-zone "eu-west-1a"} {:subnet-id "2" :availability-zone "eu-west-1b"}])
