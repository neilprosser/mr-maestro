(ns exploud.aws_test
  (:require [amazonica.aws
             [securitytoken :as sts]
             [sqs :as sqs]]
            [cheshire.core :as json]
            [environ.core :refer :all]
            [exploud.aws :refer :all]
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
      (asg-created "region" :poke "asg")
      => nil
      (provided
       (asg-created-message "asg")
       => {:Message "create"}
       (sqs/send-message :queue-url "https://region.queue.amazonaws.com/poke-account-id/autoscale-announcements"
                         :delay-seconds 0
                         :message-body "{\"Message\":\"create\"}")
       => ..send-result..))

(fact "that notifying of creation works when we have to assume another role."
      (asg-created "region" :prod "asg")
      => nil
      (provided
       (asg-created-message "asg")
       => {:Message "create"}
       (sts/assume-role {:role-arn "prod-role-arn"
                         :role-session-name "exploud"})
       => {:credentials ..creds..}
       (sqs/send-message ..creds..
                         :queue-url "https://region.queue.amazonaws.com/prod-account-id/autoscale-announcements"
                         :delay-seconds 0
                         :message-body "{\"Message\":\"create\"}")
       => ..send-result..))


(fact "that notifying of deletion works when we don't have to assume another role."
      (asg-deleted "region" :poke "asg")
      => nil
      (provided
       (asg-deleted-message "asg")
       => {:Message "delete"}
       (sqs/send-message :queue-url "https://region.queue.amazonaws.com/poke-account-id/autoscale-announcements"
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
       => {:credentials ..creds..}
       (sqs/send-message ..creds..
                         :queue-url "https://region.queue.amazonaws.com/prod-account-id/autoscale-announcements"
                         :delay-seconds 0
                         :message-body "{\"Message\":\"delete\"}")
       => ..send-result..))
