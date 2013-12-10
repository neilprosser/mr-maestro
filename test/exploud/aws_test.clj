(ns exploud.aws_test
  (:require [amazonica.aws
             [autoscaling :as auto]
             [ec2 :as ec2]
             [securitytoken :as sts]
             [sqs :as sqs]]
            [cheshire.core :as json]
            [environ.core :refer :all]
            [exploud
             [asgard :as asgard]
             [aws :refer :all]]
            [midje.sweet :refer :all]))

(fact "that the ASG creation message is correct"
      (asg-created-message "asg")
      => {:Message "message"}
      (provided
       (json/generate-string {:Event "autoscaling:ASG_LAUNCH"
                              :AutoScalingGroupName "asg"})
       => "message"))

(fact "that the ASG deletion message is correct"
      (asg-deleted-message "asg")
      => {:Message "message"}
      (provided
       (json/generate-string {:Event "autoscaling:ASG_TERMINATE"
                              :AutoScalingGroupName "asg"})
       => "message"))

(fact "that notifying of creation works when we don't have to assume another role."
      (asg-created "region" :poke "asg" {:contact "me@me.com" :important "things" :other nil})
      => nil
      (provided
       (asg-created-message "asg")
       => {:Message "create"}
       (sqs/send-message {:endpoint "region"}
                         :queue-url "https://region.queue.amazonaws.com/poke-account-id/autoscale-announcements"
                         :delay-seconds 0
                         :message-body "{\"Message\":\"create\"}")
       => ..send-result..
       (auto/put-notification-configuration {:endpoint "region"}
                                            :auto-scaling-group-name "asg"
                                            :notification-types ["autoscaling:EC2_INSTANCE_LAUNCH" "autoscaling:EC2_INSTANCE_TERMINATE"]
                                            :topic-arn "poke-autoscaling-topic-arn")
       => ..put-result..
       (auto/create-or-update-tags {:endpoint "region"} :tags [{:resource-id "asg"
                                                                :resource-type "auto-scaling-group"
                                                                :propagate-at-launch true
                                                                :key "contact"
                                                                :value "me@me.com"}
                                                               {:resource-id "asg"
                                                                :resource-type "auto-scaling-group"
                                                                :propagate-at-launch true
                                                                :key "important"
                                                                :value "things"
                                                                }])
       => ..create-asg-tags-result..
       (asgard/instances-in-asg :poke "region" "asg")
       => [{:instance {:instanceId "instance-1"}} {:instance {:instanceId "instance-2"}}]
       (ec2/create-tags {:endpoint "region"} :resources ["instance-1" "instance-2"] :tags [{:key "contact" :value "me@me.com"} {:key "important" :value "things"}])
       => ..create-instance-tags-result..))

(fact "that notifying of creation works when we have to assume another role."
      (asg-created "region" :prod "asg" {})
      => nil
      (provided
       (asg-created-message "asg")
       => {:Message "create"}
       (sts/assume-role {:role-arn "prod-role-arn"
                         :role-session-name "exploud"})
       => {:credentials {:access-key "key" :secret-key "secret"}}
       (sqs/send-message {:endpoint "region" :access-key "key" :secret-key "secret"}
                         :queue-url "https://region.queue.amazonaws.com/prod-account-id/autoscale-announcements"
                         :delay-seconds 0
                         :message-body "{\"Message\":\"create\"}")
       => ..send-result..
       (auto/put-notification-configuration {:endpoint "region" :access-key "key" :secret-key "secret"}
                                            :auto-scaling-group-name "asg"
                                            :notification-types ["autoscaling:EC2_INSTANCE_LAUNCH" "autoscaling:EC2_INSTANCE_TERMINATE"]
                                            :topic-arn "prod-autoscaling-topic-arn")
       => ..put-result..))

(fact "that notifying of creation doesn't do anything with tags when there aren't any."(asg-created "region" :poke "asg" {})
      => nil
      (provided
       (asg-created-message "asg")
       => {:Message "create"}
       (sqs/send-message {:endpoint "region"}
                         :queue-url "https://region.queue.amazonaws.com/poke-account-id/autoscale-announcements"
                         :delay-seconds 0
                         :message-body "{\"Message\":\"create\"}")
       => ..send-result..
       (auto/put-notification-configuration {:endpoint "region"}
                                            :auto-scaling-group-name "asg"
                                            :notification-types ["autoscaling:EC2_INSTANCE_LAUNCH" "autoscaling:EC2_INSTANCE_TERMINATE"]
                                            :topic-arn "poke-autoscaling-topic-arn")
       => ..put-result..))

(fact "that notifying of deletion works when we don't have to assume another role."
      (asg-deleted "region" :poke "asg")
      => nil
      (provided
       (asg-deleted-message "asg")
       => {:Message "delete"}
       (sqs/send-message {:endpoint "region"}
                         :queue-url "https://region.queue.amazonaws.com/poke-account-id/autoscale-announcements"
                         :delay-seconds 0
                         :message-body "{\"Message\":\"delete\"}")
       => ..send-result..))

(fact "that notifying of deletion works when we have to assume another role."
      (asg-deleted "region" :prod "asg")
      => nil
      (provided
       (asg-deleted-message "asg")
       => {:Message "delete"}
       (sts/assume-role {:role-arn "prod-role-arn"
                         :role-session-name "exploud"})
       => {:credentials {:access-key "key" :secret-key "secret"}}
       (sqs/send-message {:endpoint "region" :access-key "key" :secret-key "secret"}
                         :queue-url "https://region.queue.amazonaws.com/prod-account-id/autoscale-announcements"
                         :delay-seconds 0
                         :message-body "{\"Message\":\"delete\"}")
       => ..send-result..))
