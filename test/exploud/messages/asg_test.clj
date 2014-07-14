(ns exploud.messages.asg-test
  (:require [amazonica.aws
             [autoscaling :as auto]
             [elasticloadbalancing :as elb]
             [sqs :as sqs]]
            [exploud.aws :as aws]
            [exploud.messages.asg :refer :all]
            [midje.sweet :refer :all]
            [ring.util.codec :refer [base64-encode]]))

(def create-launch-configuration-params
  {:application "application"
   :environment "environment"
   :region "region"
   :new-state {:image-details {:id "image"}
               :launch-configuration-name "lc"
               :selected-security-group-ids ["sg-1" "sg-2"]
               :tyranitar {:application-properties {}
                           :deployment-params {:instance-type "instance"}
                           :launch-data []}
               :user-data "user-data"}})

(fact "that creating a launch configuration errors if there is an exception"
      (create-launch-configuration {:parameters create-launch-configuration-params}) => (contains {:status :error})
      (provided
       (aws/launch-configuration "lc" "environment" "region") => nil
       (aws/config anything anything) => {}
       (auto/create-launch-configuration anything
                                         :launch-configuration-name "lc"
                                         :iam-instance-profile "application"
                                         :image-id "image"
                                         :instance-type "instance"
                                         :key-name "exploud"
                                         :security-groups ["sg-1" "sg-2"]
                                         :user-data ..user-data..)
       =throws=> (ex-info "Busted" {})
       (base64-encode anything) => ..user-data..))

(fact "that creating a launch configuration is successful if the launch configuration already exists"
      (create-launch-configuration {:parameters create-launch-configuration-params}) => (contains {:status :success})
      (provided
       (aws/launch-configuration "lc" "environment" "region") => {}))

(fact "that creating a launch configuration returns the correct response if it succeeds"
      (create-launch-configuration {:parameters create-launch-configuration-params}) => (contains {:status :success})
      (provided
       (aws/launch-configuration "lc" "environment" "region") => nil
       (aws/config anything anything) => {}
       (auto/create-launch-configuration anything
                                         :launch-configuration-name "lc"
                                         :iam-instance-profile "application"
                                         :image-id "image"
                                         :instance-type "instance"
                                         :key-name "exploud"
                                         :security-groups ["sg-1" "sg-2"]
                                         :user-data ..user-data..)
       => ..lc-result..
       (base64-encode anything) => ..user-data..))

(fact "that attempting to create a launch configuration for a deployment with no previous state is successful and does nothing"
      (create-launch-configuration {:parameters (assoc create-launch-configuration-params :undo true :previous-state nil)}) => (contains {:status :success}))

(def create-auto-scaling-group-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"
               :auto-scaling-group-tags [{:key "ATag"
                                          :value "hello"
                                          :propagate-at-launch true
                                          :resource-type "auto-scaling-group"
                                          :resource-id "asg"}]
               :availability-zones ["regiona" "regionb"]
               :launch-configuration-name "lc"
               :selected-subnets ["1" "2"]
               :termination-policies ["Default"]
               :tyranitar {:deployment-params {:default-cooldown 15
                                               :desired-capacity 3
                                               :health-check-grace-period 345
                                               :health-check-type "ELB"
                                               :max-size 4
                                               :min-size 2
                                               :selected-load-balancers ["lb-1" "lb-2"]
                                               :selected-zones ["a" "b"]
                                               :termination-policy "Default"}}
               :vpc-zone-identifier "1,2"}})

(fact "that creating an auto scaling group errors if there is an exception"
      (create-auto-scaling-group {:parameters create-auto-scaling-group-params}) => (contains {:status :error})
      (provided
       (aws/auto-scaling-group "asg" "environment" "region") => nil
       (aws/config anything anything) => {}
       (auto/create-auto-scaling-group anything
                                       :auto-scaling-group-name "asg"
                                       :availability-zones ["regiona" "regionb"]
                                       :default-cooldown 15
                                       :desired-capacity 0
                                       :health-check-grace-period 345
                                       :health-check-type "ELB"
                                       :launch-configuration-name "lc"
                                       :load-balancer-names ["lb-1" "lb-2"]
                                       :max-size 0
                                       :min-size 0
                                       :tags [{:key "ATag"
                                               :value "hello"
                                               :propagate-at-launch true
                                               :resource-type "auto-scaling-group"
                                               :resource-id "asg"}]
                                       :termination-policies ["Default"]
                                       :vpc-zone-identifier "1,2") =throws=> (ex-info "Busted" {})))

(fact "that creating an auto scaling group is successful when it already exists"
      (create-auto-scaling-group {:parameters create-auto-scaling-group-params}) => (contains {:status :success})
      (provided
       (aws/auto-scaling-group "asg" "environment" "region") => {}))

(fact "that creating an auto scaling group returns the correct response if it succeeds"
      (create-auto-scaling-group {:parameters create-auto-scaling-group-params}) => (contains {:status :success})
      (provided
       (aws/auto-scaling-group "asg" "environment" "region") => nil
       (aws/config anything anything) => {}
       (auto/create-auto-scaling-group anything
                                       :auto-scaling-group-name "asg"
                                       :availability-zones ["regiona" "regionb"]
                                       :default-cooldown 15
                                       :desired-capacity 0
                                       :health-check-grace-period 345
                                       :health-check-type "ELB"
                                       :launch-configuration-name "lc"
                                       :load-balancer-names ["lb-1" "lb-2"]
                                       :max-size 0
                                       :min-size 0
                                       :tags [{:key "ATag"
                                               :value "hello"
                                               :propagate-at-launch true
                                               :resource-type "auto-scaling-group"
                                               :resource-id "asg"}]
                                       :termination-policies ["Default"]
                                       :vpc-zone-identifier "1,2")
       => ..asg-result..))

(fact "that attempting to create an auto scaling group for a deployment with no previous state is successful and does nothing"
      (create-auto-scaling-group {:parameters (assoc create-auto-scaling-group-params :undo true :previous-state nil)}) => (contains {:status :success}))

(def disable-adding-instances-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"}})

(fact "that disabling adding instances errors if there is an exception"
      (disable-adding-instances {:parameters disable-adding-instances-params}) => (contains {:status :error})
      (provided
       (aws/config anything anything) => {}
       (auto/suspend-processes anything
                               :auto-scaling-group-name "asg"
                               :scaling-processes ["AddToLoadBalancer"]) =throws=> (ex-info "Busted" {})))

(fact "that disabling adding instances returns the correct response if it succeeds"
      (disable-adding-instances {:parameters disable-adding-instances-params}) => (contains {:status :success})
      (provided
       (aws/config anything anything) => {}
       (auto/suspend-processes anything
                               :auto-scaling-group-name "asg"
                               :scaling-processes ["AddToLoadBalancer"])
       => ..suspend-result..))

(fact "that attempting to disable adding instances for a deployment with no previous state is successful and does nothing"
      (disable-adding-instances {:parameters (assoc disable-adding-instances-params :undo true :previous-state nil)}) => (contains {:status :success}))

(def add-scaling-notifications-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"
               :created true}})

(fact "that adding scaling notifications errors if there is an exception"
      (add-scaling-notifications {:parameters add-scaling-notifications-params}) => (contains {:status :error})
      (provided
       (aws/autoscaling-topic "environment") => ..topic..
       (aws/config anything anything) => {}
       (auto/put-notification-configuration anything
                                            :auto-scaling-group-name "asg"
                                            :notification-types ["autoscaling:EC2_INSTANCE_LAUNCH" "autoscaling:EC2_INSTANCE_TERMINATE"]
                                            :topic-arn ..topic..) =throws=> (ex-info "Busted" {})))

(fact "that attempting to add scaling notifications when nothing was created skips the step"
      (add-scaling-notifications {:parameters (assoc-in add-scaling-notifications-params [:new-state :created] nil)}) => (contains {:status :success}))

(fact "that adding scaling notifications returns the correct response if it succeeds"
      (add-scaling-notifications {:parameters add-scaling-notifications-params}) => (contains {:status :success})
      (provided
       (aws/autoscaling-topic "environment") => ..topic..
       (aws/config anything anything) => {}
       (auto/put-notification-configuration anything
                                            :auto-scaling-group-name "asg"
                                            :notification-types ["autoscaling:EC2_INSTANCE_LAUNCH" "autoscaling:EC2_INSTANCE_TERMINATE"]
                                            :topic-arn ..topic..) => ..notification-result..))

(fact "that attempting to add scaling notifications to a deployment with no previous state is successful and does nothing"
      (add-scaling-notifications {:parameters (assoc add-scaling-notifications-params :undo true :previous-state nil)}) => (contains {:status :success}))

(def notify-of-auto-scaling-group-creation-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"
               :created true}})

(fact "that notifying of auto scaling group creation errors if there is an exception"
      (notify-of-auto-scaling-group-creation {:parameters notify-of-auto-scaling-group-creation-params}) => (contains {:status :error})
      (provided
       (aws/announcement-queue-url "region" "environment") => ..announcement-queue..
       (aws/asg-created-message "asg") => ..message..
       (aws/config anything anything) => {}
       (sqs/send-message anything
                         :queue-url ..announcement-queue..
                         :delay-seconds 0
                         :message-body ..message..) =throws=> (ex-info "Busted" {})))

(fact "that attempting to notify of auto scaling group creation when nothing was created skips the step"
      (notify-of-auto-scaling-group-creation {:parameters (assoc-in notify-of-auto-scaling-group-creation-params [:new-state :created] nil)}) => (contains {:status :success}))

(fact "that notifying of auto scaling group creation gives the correct response if it succeeds"
      (notify-of-auto-scaling-group-creation {:parameters notify-of-auto-scaling-group-creation-params}) => (contains {:status :success})
      (provided
       (aws/announcement-queue-url "region" "environment") => ..announcement-queue..
       (aws/asg-created-message "asg") => ..message..
       (aws/config anything anything) => {}
       (sqs/send-message anything
                         :queue-url ..announcement-queue..
                         :delay-seconds 0
                         :message-body ..message..) => ..send-result..))

(fact "that attempting to notify about auto scaling group creation for a deployment with no previous state is successful and does nothing"
      (notify-of-auto-scaling-group-creation {:parameters (assoc notify-of-auto-scaling-group-creation-params :undo true :previous-state nil)}) => (contains {:status :success}))

(def resize-auto-scaling-group-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"
               :created true
               :tyranitar {:deployment-params {:desired-capacity 2
                                               :max 3
                                               :min 1}}}})

(fact "that resizing an auto scaling group errors if there is an exception"
      (resize-auto-scaling-group {:parameters resize-auto-scaling-group-params}) => (contains {:status :error})
      (provided
       (aws/config anything anything) => {}
       (auto/update-auto-scaling-group anything
                                       :auto-scaling-group-name "asg"
                                       :desired-capacity 2
                                       :max-size 3
                                       :min-size 1) =throws=> (ex-info "Busted" {})))

(fact "that attempting to resize an auto scaling group skips the step if nothing was created"
      (resize-auto-scaling-group {:parameters (assoc-in resize-auto-scaling-group-params [:new-state :created] nil)}) => (contains {:status :success}))

(fact "that resizing an auto scaling group returns the correct response if it succeeds"
      (resize-auto-scaling-group {:parameters resize-auto-scaling-group-params}) => (contains {:status :success})
      (provided
       (aws/config anything anything) => {}
       (auto/update-auto-scaling-group anything
                                       :auto-scaling-group-name "asg"
                                       :desired-capacity 2
                                       :max-size 3
                                       :min-size 1) => ..update-result..))

(fact "that attempting to resize an auto scaling group for a deployment with no previous state is successful and does nothing"
      (resize-auto-scaling-group {:parametrs (assoc resize-auto-scaling-group-params :undo true :previous-state nil)}) => (contains {:status :success}))

(def wait-for-instances-to-exist-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"
               :tyranitar {:deployment-params {:max 2
                                               :min 1}}}})

(fact "that waiting for instances to exist errors if there is an exception"
      (wait-for-instances-to-exist {:attempt 1
                                    :parameters wait-for-instances-to-exist-params})
      => (contains {:status :error})
      (provided
       (aws/config anything anything) => {}
       (auto/describe-auto-scaling-groups anything
                                          :auto-scaling-group-names ["asg"]
                                          :max-records 1) =throws=> (ex-info "Busted" {})))

(fact "that waiting for instances to exist retries if there aren't enough instances"
      (wait-for-instances-to-exist {:attempt 1
                                    :parameters wait-for-instances-to-exist-params})
      => (contains {:status :retry
                    :backoff-ms 10000})
      (provided
       (aws/config anything anything) => {}
       (auto/describe-auto-scaling-groups anything
                                          :auto-scaling-group-names ["asg"]
                                          :max-records 1) => {:auto-scaling-groups [{:instances []}]}))

(fact "that waiting for instances to exist returns the right response if it succeeds"
      (wait-for-instances-to-exist {:attempt 1
                                    :parameters wait-for-instances-to-exist-params})
      => (contains {:status :success})
      (provided
       (aws/config anything anything) => {}
       (auto/describe-auto-scaling-groups anything
                                          :auto-scaling-group-names ["asg"]
                                          :max-records 1) => {:auto-scaling-groups [{:instances [{}]}]}))

(fact "that waiting for instances to exist too many times is an error"
      (wait-for-instances-to-exist {:attempt 50
                                    :parameters wait-for-instances-to-exist-params})
      => (contains {:status :error})
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => []))

(fact "that waiting for instances to exist for a deployment with no previous state does nothing and is successful"
      (wait-for-instances-to-exist {:attempt 1
                                    :parameters (assoc wait-for-instances-to-exist-params :undo true :previous-state nil)}))

(def wait-for-instances-to-be-in-service-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"
               :tyranitar {:deployment-params {:max 2
                                               :min 1}}}})

(fact "that waiting for instances to be in service errors if there is an exception"
      (wait-for-instances-to-be-in-service {:attempt 1
                                            :parameters wait-for-instances-to-be-in-service-params})
      => (contains {:status :error})
      (provided
       (aws/config anything anything) => {}
       (auto/describe-auto-scaling-groups anything
                                          :auto-scaling-group-names ["asg"]
                                          :max-records 1) =throws=> (ex-info "Busted" {})))

(fact "that waiting for instances to be in service retries if there aren't enough instances 'InService'"
      (wait-for-instances-to-be-in-service {:attempt 1
                                            :parameters wait-for-instances-to-be-in-service-params})
      => (contains {:status :retry
                    :backoff-ms 10000})
      (provided
       (aws/config anything anything) => {}
       (auto/describe-auto-scaling-groups anything
                                          :auto-scaling-group-names ["asg"]
                                          :max-records 1) => {:auto-scaling-groups [{:instances [{:lifecycle-state "Busted"}]}]}))

(fact "that waiting for instances to be in service returns the correct response if it succeeds"
      (wait-for-instances-to-be-in-service {:attempt 1
                                            :parameters wait-for-instances-to-be-in-service-params})
      => (contains {:status :success})
      (provided
       (aws/config anything anything) => {}
       (auto/describe-auto-scaling-groups anything
                                          :auto-scaling-group-names ["asg"]
                                          :max-records 1) => {:auto-scaling-groups [{:instances [{:lifecycle-state "NotReadyYet"} {:lifecycle-state "InService"}]}]}))

(fact "that waiting for instances to be in service too many times is an error"
      (wait-for-instances-to-be-in-service {:attempt 50
                                            :parameters wait-for-instances-to-be-in-service-params})
      => (contains {:status :error})
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => [{:lifecycle-state "NotInService"}]))

(fact "that attempting to wait for instances to be in service for a deployment with no previous state is successful and does nothing"
      (wait-for-instances-to-be-in-service {:attempt 1
                                            :parameters (assoc wait-for-instances-to-be-in-service-params :undo true :previous-state nil)}))

(def disable-instance-launching-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"}})

(fact "that disabling launching instances errors if there is an exception"
      (disable-instance-launching {:parameters disable-instance-launching-params}) => (contains {:status :error})
      (provided
       (aws/config anything anything) => {}
       (auto/suspend-processes anything
                               :auto-scaling-group-name "asg"
                               :scaling-processes ["Launch"]) =throws=> (ex-info "Busted" {})))

(fact "that disabling launching instances returns the correct response if it succeeds"
      (disable-instance-launching {:parameters disable-instance-launching-params}) => (contains {:status :success})
      (provided
       (aws/config anything anything) => {}
       (auto/suspend-processes anything
                               :auto-scaling-group-name "asg"
                               :scaling-processes ["Launch"])
       => ..suspend-result..))

(fact "that attempting to disable instance launching for a deployment with no previous state is successful and does nothing"
      (disable-instance-launching {:parameters (assoc disable-instance-launching-params :undo true :previous-state nil)}) => (contains {:status :success}))

(def disable-instance-termination-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"}})

(fact "that disabling terminating instances errors if there is an exception"
      (disable-instance-termination {:parameters disable-instance-termination-params}) => (contains {:status :error})
      (provided
       (aws/config anything anything) => {}
       (auto/suspend-processes anything
                               :auto-scaling-group-name "asg"
                               :scaling-processes ["Terminate"]) =throws=> (ex-info "Busted" {})))

(fact "that disabling terminating instances returns the correct response if it succeeds"
      (disable-instance-termination {:parameters disable-instance-termination-params}) => (contains {:status :success})
      (provided
       (aws/config anything anything) => {}
       (auto/suspend-processes anything
                               :auto-scaling-group-name "asg"
                               :scaling-processes ["Terminate"])
       => ..suspend-result..))

(fact "that attempting to disable instance termination for a deployment with no previous state is successful and does nothing"
      (disable-instance-termination {:parameters (assoc disable-instance-termination-params :undo true :previous-state nil)}) => (contains {:status :success}))

(def enable-instance-launching-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"}})

(fact "that enabling launching instances errors if there is an exception"
      (enable-instance-launching {:parameters enable-instance-launching-params}) => (contains {:status :error})
      (provided
       (aws/config anything anything) => {}
       (auto/resume-processes anything
                              :auto-scaling-group-name "asg"
                              :scaling-processes ["Launch"]) =throws=> (ex-info "Busted" {})))

(fact "that enabling launching instances returns the correct response if it succeeds"
      (enable-instance-launching {:parameters enable-instance-launching-params}) => (contains {:status :success})
      (provided
       (aws/config anything anything) => {}
       (auto/resume-processes anything
                              :auto-scaling-group-name "asg"
                              :scaling-processes ["Launch"])
       => ..suspend-result..))

(fact "that attempting to enable launching instances for a deployment with no previous state is successful and does nothing"
      (enable-instance-launching {:parameters (assoc enable-instance-launching-params :undo true :previous-state nil)}) => (contains {:status :success}))

(def enable-instance-termination-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"}})

(fact "that enabling terminating instances errors if there is an exception"
      (enable-instance-termination {:parameters enable-instance-termination-params}) => (contains {:status :error})
      (provided
       (aws/config anything anything) => {}
       (auto/resume-processes anything
                              :auto-scaling-group-name "asg"
                              :scaling-processes ["Terminate"]) =throws=> (ex-info "Busted" {})))

(fact "that enabling terminating instances returns the correct response if it succeeds"
      (enable-instance-termination {:parameters enable-instance-termination-params}) => (contains {:status :success})
      (provided
       (aws/config anything anything) => {}
       (auto/resume-processes anything
                              :auto-scaling-group-name "asg"
                              :scaling-processes ["Terminate"])
       => ..suspend-result..))

(fact "that attempting to enable instance termination for a deployment with no previous state is successful and does nothing"
      (enable-instance-termination {:parameters (assoc enable-instance-termination-params :undo true :previous-state nil)}) => (contains {:status :success}))

(def enable-adding-instances-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"}})

(fact "that enabling adding instances errors if there is an exception"
      (enable-adding-instances {:parameters enable-adding-instances-params}) => (contains {:status :error})
      (provided
       (aws/config anything anything) => {}
       (auto/resume-processes anything
                              :auto-scaling-group-name "asg"
                              :scaling-processes ["AddToLoadBalancer"]) =throws=> (ex-info "Busted" {})))

(fact "that enabling adding instances returns the correct response if it succeeds"
      (enable-adding-instances {:parameters enable-adding-instances-params}) => (contains {:status :success})
      (provided
       (aws/config anything anything) => {}
       (auto/resume-processes anything
                              :auto-scaling-group-name "asg"
                              :scaling-processes ["AddToLoadBalancer"])
       => ..suspend-result..))

(fact "that attempting to enable adding instances to a deployment with no previous state is successful and does nothing"
      (enable-adding-instances {:parameters (assoc enable-adding-instances-params :undo true :previous-state nil)}) => (contains {:status :success}))

(def register-instances-with-load-balancers-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"
               :tyranitar {:deployment-params {:selected-load-balancers ["lb-1" "lb-2"]}}}})

(fact "that attempting to register instances to no load balancers is successful"
      (register-instances-with-load-balancers {:parameters {:new-state {:tyranitar {:deployment-params {:selected-load-balancers []}}}}}) => (contains {:status :success}))

(fact "that attempting to register when there are no instances is successful"
      (register-instances-with-load-balancers {:parameters register-instances-with-load-balancers-params}) => (contains {:status :success})
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => []))

(fact "that attempting to register when there is an exception returns an error"
      (register-instances-with-load-balancers {:parameters register-instances-with-load-balancers-params}) => (contains {:status :error})
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") =throws=> (ex-info "Busted" {})))

(fact "that attempting to register returns the correct response"
      (register-instances-with-load-balancers {:parameters register-instances-with-load-balancers-params}) => (contains {:status :success})
      (provided
       (aws/auto-scaling-group-instances "asg" "environment" "region") => [{:instance-id "i-1"} {:instance-id "i-2"}]
       (aws/config anything anything) => {}
       (elb/register-instances-with-load-balancer anything
                                                  :load-balancer-name "lb-1"
                                                  :instances [{:instance-id "i-1"}
                                                              {:instance-id "i-2"}])
       => ..register-result-1..
       (elb/register-instances-with-load-balancer anything
                                                  :load-balancer-name "lb-2"
                                                  :instances [{:instance-id "i-1"}
                                                              {:instance-id "i-2"}])
       => ..register-result-2..))

(fact "that attempting to register instances with load balancers for a deployment with no previous state is successful and does nothing"
      (register-instances-with-load-balancers {:parameters (assoc register-instances-with-load-balancers-params :undo true :previous-state nil)}) => (contains {:status :success}))

(def add-scheduled-actions-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"
               :tyranitar {:deployment-params {:scheduled-actions {:scale-up {:cron "30 4 * * *"
                                                                              :desired-capacity 1
                                                                              :max 1
                                                                              :min 1}}}}}
   :previous-state {:auto-scaling-group-name "old-asg"}})

(fact "that getting an error while adding scheduled actions is an error"
      (add-scheduled-actions {:parameters add-scheduled-actions-params}) => (contains {:status :error})
      (provided
       (aws/config anything anything) => {}
       (auto/put-scheduled-update-group-action anything
                                               :auto-scaling-group-name "asg"
                                               :desired-capacity 1
                                               :max-size 1
                                               :min-size 1
                                               :recurrence "30 4 * * *"
                                               :scheduled-action-name "asg-scale-up") =throws=> (ex-info "Busted" {})))

(fact "that we don't do anything if there is no auto scaling group name"
      (add-scheduled-actions {:parameters (assoc (assoc add-scheduled-actions-params :previous-state nil) :undo true)}) => (contains {:status :success}))

(fact "that we don't do anything if there are no scheduled actions"
      (add-scheduled-actions {:parameters (assoc-in add-scheduled-actions-params [:new-state :tyranitar :deployment-params :scheduled-actions] nil)}) => (contains {:status :success}))

(fact "that adding scheduled actions calls what we expect"
      (add-scheduled-actions {:parameters add-scheduled-actions-params}) => (contains {:status :success})
      (provided
       (aws/config anything anything) => {}
       (auto/put-scheduled-update-group-action anything
                                               :auto-scaling-group-name "asg"
                                               :desired-capacity 1
                                               :max-size 1
                                               :min-size 1
                                               :recurrence "30 4 * * *"
                                               :scheduled-action-name "asg-scale-up") => nil))

(def disable-old-instance-launching-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"}
   :previous-state {:auto-scaling-group-name "old-asg"}})

(fact "that disabling instance launching on old auto scaling group succeeds if there's no previous group"
      (disable-old-instance-launching {}) => (contains {:status :success}))

(fact "that getting an exception while disabling instance launching on old auto scaling group is an error"
      (disable-old-instance-launching {:id "id"
                                       :attempt 1
                                       :parameters disable-old-instance-launching-params})
      => (contains {:status :error})
      (provided
       (disable-instance-launching anything) =throws=> (ex-info "Busted" {})))

(fact "that disabling instance launching on old auto scaling group calls with correct params"
      (disable-old-instance-launching {:id "id"
                                       :attempt 1
                                       :parameters disable-old-instance-launching-params})
      => (contains {:status :success})
      (provided
       (disable-instance-launching {:id "id"
                                    :attempt 1
                                    :parameters {:environment "environment"
                                                 :region "region"
                                                 :new-state {:auto-scaling-group-name "old-asg"}}}) => ..result..))

(fact "that attempting to disabling instance launching for a deployment with no previous state does nothing and is successful"
      (disable-old-instance-launching {:parameters (assoc disable-old-instance-launching-params :previous-state nil)}) => (contains {:status :success}))

(def disable-old-instance-termination-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"}
   :previous-state {:auto-scaling-group-name "old-asg"}})

(fact "that disabling instance termination on old auto scaling group succeeds if there's no previous group"
      (disable-old-instance-termination {}) => (contains {:status :success}))

(fact "that getting an exception while disabling instance termination on old auto scaling group is an error"
      (disable-old-instance-termination {:id "id"
                                         :attempt 1
                                         :parameters disable-old-instance-termination-params})
      => (contains {:status :error})
      (provided
       (disable-instance-termination anything) =throws=> (ex-info "Busted" {})))

(fact "that disabling instance termination on old auto scaling group calls with correct params"
      (disable-old-instance-termination {:id "id"
                                         :attempt 1
                                         :parameters disable-old-instance-termination-params}) => (contains {:status :success})
                                         (provided
                                          (disable-instance-termination {:id "id"
                                                                         :attempt 1
                                                                         :parameters {:environment "environment"
                                                                                      :region "region"
                                                                                      :new-state {:auto-scaling-group-name "old-asg"}}}) => ..result..))

(fact "that attempting to disable old instance termination for a deployment with no previous state is successful and does nothing"
      (disable-old-instance-termination (assoc disable-old-instance-termination-params :previous-state nil)) => (contains {:status :success}))

(def disable-old-adding-instances-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"}
   :previous-state {:auto-scaling-group-name "old-asg"}})

(fact "that disabling adding instances on old auto scaling group succeeds if there's no previous group"
      (disable-old-adding-instances {}) => (contains {:status :success}))

(fact "that getting an exception while disabling adding instances on old auto scaling group is an error"
      (disable-old-adding-instances {:id "id"
                                     :attempt 1
                                     :parameters disable-old-adding-instances-params})
      => (contains {:status :error})
      (provided
       (disable-adding-instances anything) =throws=> (ex-info "Busted" {})))

(fact "that disabling adding instances on old auto scaling group calls with correct params"
      (disable-old-adding-instances {:id "id"
                                     :attempt 1
                                     :parameters disable-old-instance-termination-params})
      => (contains {:status :success})
      (provided
       (disable-adding-instances {:id "id"
                                  :attempt 1
                                  :parameters {:environment "environment"
                                               :region "region"
                                               :new-state {:auto-scaling-group-name "old-asg"}}}) => ..result..))

(fact "that attempting to disable old instance adding for a deployment with no previous state is successful and does nothing"
      (disable-old-adding-instances (assoc disable-old-adding-instances-params :previous-state nil)) => (contains {:status :success}))

(def deregister-old-instances-from-load-balancers-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"
               :tyranitar {:deployment-params {:selected-load-balancers ["lb-3" "lb-4"]}}}
   :previous-state {:auto-scaling-group-name "old-asg"
                    :tyranitar {:deployment-params {:selected-load-balancers ["lb-1" "lb-2"]}}}})

(fact "that attempting to deregister instances from no load balancers is successful"
      (deregister-old-instances-from-load-balancers {:parameters {:previous-state {:auto-scaling-group-name "old-asg"
                                                                                   :tyranitar {:deployment-params {:selected-load-balancers []}}}}}) => (contains {:status :success}))

(fact "that attempting to deregister when there are no instances is successful"
      (deregister-old-instances-from-load-balancers {:parameters deregister-old-instances-from-load-balancers-params}) => (contains {:status :success})
      (provided
       (aws/auto-scaling-group-instances "old-asg" "environment" "region") => []))

(fact "that attempting to deregister when there is an exception returns an error"
      (deregister-old-instances-from-load-balancers {:parameters deregister-old-instances-from-load-balancers-params}) => (contains {:status :error})
      (provided
       (aws/auto-scaling-group-instances "old-asg" "environment" "region") =throws=> (ex-info "Busted" {})))

(fact "that attempting to deregister returns the correct response"
      (deregister-old-instances-from-load-balancers {:parameters deregister-old-instances-from-load-balancers-params}) => (contains {:status :success})
      (provided
       (aws/auto-scaling-group-instances "old-asg" "environment" "region") => [{:instance-id "i-1"} {:instance-id "i-2"}]
       (aws/config anything anything) => {}
       (elb/describe-load-balancers anything
                                    :load-balancer-names ["lb-1"])
       => {:load-balancer-descriptions [{:instances [{:instance-id "i-1"}]}]}
       (elb/deregister-instances-from-load-balancer anything
                                                    :load-balancer-name "lb-1"
                                                    :instances [{:instance-id "i-1"}])
       => ..register-result-1..
       (elb/describe-load-balancers anything
                                    :load-balancer-names ["lb-2"])
       => {:load-balancer-descriptions [{:instances [{:instance-id "i-1"}
                                                     {:instance-id "i-2"}]}]}
       (elb/deregister-instances-from-load-balancer anything
                                                    :load-balancer-name "lb-2"
                                                    :instances [{:instance-id "i-2"}
                                                                {:instance-id "i-1"}])
       => ..register-result-2..))

(fact "that attempting to deregister doesn't deregister anything if none of the instances are registered"
      (deregister-old-instances-from-load-balancers {:parameters deregister-old-instances-from-load-balancers-params}) => (contains {:status :success})
      (provided
       (aws/auto-scaling-group-instances "old-asg" "environment" "region") => [{:instance-id "i-1"} {:instance-id "i-2"}]
       (aws/config anything anything) => {}
       (elb/describe-load-balancers anything
                                    :load-balancer-names ["lb-1"])
       => {:load-balancer-descriptions [{:instances []}]}
       (elb/deregister-instances-from-load-balancer anything
                                                    :load-balancer-name "lb-1"
                                                    :instances [])
       => ..register-result-1.. :times 0
       (elb/describe-load-balancers anything
                                    :load-balancer-names ["lb-2"])
       => {:load-balancer-descriptions [{:instances [{:instance-id "i-1"}
                                                     {:instance-id "i-2"}]}]}
       (elb/deregister-instances-from-load-balancer anything
                                                    :load-balancer-name "lb-2"
                                                    :instances [{:instance-id "i-2"}
                                                                {:instance-id "i-1"}])
       => ..register-result-2..))

(fact "that attempting to deregister old instances from load balancers for a deployment with no previous state is successful and does nothing"
      (deregister-old-instances-from-load-balancers {:parameters (assoc deregister-old-instances-from-load-balancers-params :previous-state nil)}) => (contains {:status :success}))

(def notify-of-auto-scaling-group-deletion-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"}
   :previous-state {:auto-scaling-group-name "old-asg"}})

(fact "that notifying of auto scaling group deletion errors if there is an exception"
      (notify-of-auto-scaling-group-deletion {:parameters notify-of-auto-scaling-group-deletion-params}) => (contains {:status :error})
      (provided
       (aws/announcement-queue-url "region" "environment") => ..announcement-queue..
       (aws/asg-deleted-message "old-asg") => ..message..
       (aws/config anything anything) => {}
       (sqs/send-message anything
                         :queue-url ..announcement-queue..
                         :delay-seconds 0
                         :message-body ..message..) =throws=> (ex-info "Busted" {})))

(fact "that notifying of auto scaling group deletion gives the correct response if it succeeds"
      (notify-of-auto-scaling-group-deletion {:parameters notify-of-auto-scaling-group-deletion-params}) => (contains {:status :success})
      (provided
       (aws/announcement-queue-url "region" "environment") => ..announcement-queue..
       (aws/asg-deleted-message "old-asg") => ..message..
       (aws/config anything anything) => {}
       (sqs/send-message anything
                         :queue-url ..announcement-queue..
                         :delay-seconds 0
                         :message-body ..message..) => ..send-result..))

(fact "that attempting to notify of auto scaling group deletion for a deployment with no previous state is successful and does nothing"
      (notify-of-auto-scaling-group-deletion {:parameters (assoc notify-of-auto-scaling-group-deletion-params :previous-state nil)}) => (contains {:status :success}))

(def delete-old-auto-scaling-group-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"}
   :previous-state {:auto-scaling-group-name "old-asg"}})

(fact "that attempting to delete an old auto scaling group when not present is successful"
      (delete-old-auto-scaling-group {:parameters {}}) => (contains {:status :success}))

(fact "that attempting to delete an old auto scaling group which has already been deleted is successful"
      (delete-old-auto-scaling-group {:parameters delete-old-auto-scaling-group-params}) => (contains {:status :success})
      (provided
       (aws/auto-scaling-group "old-asg" "environment" "region") => nil
       (auto/delete-auto-scaling-group anything
                                       :auto-scaling-group-name "old-asg"
                                       :force-delete true) => nil :times 0))

(fact "that getting an exception while deleting an old auto scaling group is an error"
      (delete-old-auto-scaling-group {:parameters delete-old-auto-scaling-group-params}) => (contains {:status :error})
      (provided
       (aws/auto-scaling-group "old-asg" "environment" "region") => {}
       (aws/config anything anything) => {}
       (auto/delete-auto-scaling-group anything
                                       :auto-scaling-group-name "old-asg"
                                       :force-delete true) =throws=> (ex-info "Busted" {})))

(fact "that deleting an old auto scaling group successfully gives the right response"
      (delete-old-auto-scaling-group {:parameters delete-old-auto-scaling-group-params}) => (contains {:status :success})
      (provided
       (aws/auto-scaling-group "old-asg" "environment" "region") => {}
       (aws/config anything anything) => {}
       (auto/delete-auto-scaling-group anything
                                       :auto-scaling-group-name "old-asg"
                                       :force-delete true) => ..delete-result..))

(fact "that attempting to delete an old auto scaling group for a deployment with no previous state is successful and does nothing"
      (delete-old-auto-scaling-group {:parameters (assoc delete-old-auto-scaling-group-params :previous-state nil)}) => (contains {:status :success}))

(def wait-for-old-auto-scaling-group-deletion-params
  {:environment "environment"
   :region "region"
   :new-state {:auto-scaling-group-name "asg"}
   :previous-state {:auto-scaling-group-name "old-asg"}})

(fact "that waiting for the deletion of an old auto scaling group when not present is successful"
      (wait-for-old-auto-scaling-group-deletion {:attempt 1
                                                 :parameters {}}) => (contains {:status :success}))

(fact "that getting an exception while waiting for the deletion of and old auto scaling group is an error"
      (wait-for-old-auto-scaling-group-deletion {:attempt 1
                                                 :parameters wait-for-old-auto-scaling-group-deletion-params})
      => (contains {:status :error})
      (provided
       (aws/auto-scaling-group "old-asg" "environment" "region") =throws=> (ex-info "Busted" {})))

(fact "that waiting for the deletion of a no-longer-present auto scaling group is successful"
      (wait-for-old-auto-scaling-group-deletion {:attempt 1
                                                 :parameters wait-for-old-auto-scaling-group-deletion-params})
      => (contains {:status :success})
      (provided
       (aws/auto-scaling-group "old-asg" "environment" "region") => nil))

(fact "that waiting for the deletion of a still-present auto scaling group retries"
      (wait-for-old-auto-scaling-group-deletion {:attempt 1
                                                 :parameters wait-for-old-auto-scaling-group-deletion-params})
      => {:status :retry
          :backoff-ms 10000}
      (provided
       (aws/auto-scaling-group "old-asg" "environment" "region") => {}))

(fact "that waiting for the deletion of an auto scaling group too many times is an error"
      (wait-for-old-auto-scaling-group-deletion {:attempt 50
                                                 :parameters wait-for-old-auto-scaling-group-deletion-params})
      => (contains {:status :error})
      (provided
       (aws/auto-scaling-group "old-asg" "environment" "region") => {}))

(fact "that attempting to wait for the deletion of an old auto scaling group for a deployment with no previous state is successful and does nothing"
      (wait-for-old-auto-scaling-group-deletion {:attempt 1
                                                 :parameters (assoc wait-for-old-auto-scaling-group-deletion-params :previous-state nil)}) => (contains {:status :success}))

(fact "that waiting for the deletion of an old auto scaling group uses given maximum attempts"
      (wait-for-old-auto-scaling-group-deletion {:attempt 12
                                                 :parameters (assoc-in wait-for-old-auto-scaling-group-deletion-params [:previous-state :tyranitar :deployment-params :auto-scaling-group-deletion-attempts] 12)})
      => (contains {:status :error})
      (provided
       (aws/auto-scaling-group "old-asg" "environment" "region") => {}))

(def delete-old-launch-configuration-params
  {:environment "environment"
   :region "region"
   :new-state {:launch-configuration-name "lc"}
   :previous-state {:launch-configuration-name "old-lc"}})

(fact "that attempting to delete an old launch configuration when not present is successful"
      (delete-old-launch-configuration {:parameters {}}) => (contains {:status :success}))

(fact "that attempting to delete an old launch configuration which has already been deleted is successful"
      (delete-old-launch-configuration {:parameters delete-old-launch-configuration-params})
      => (contains {:status :success})
      (provided
       (aws/launch-configuration "old-lc" "environment" "region") => nil
       (auto/delete-launch-configuration anything
                                         :launch-configuration-name "old-lc") => nil :times 0))

(fact "that getting an exception while deleting an old launch configuration is an error"
      (delete-old-launch-configuration {:parameters delete-old-launch-configuration-params}) => (contains {:status :error})
      (provided
       (aws/launch-configuration "old-lc" "environment" "region") => {}
       (aws/config anything anything) => {}
       (auto/delete-launch-configuration anything
                                         :launch-configuration-name "old-lc") =throws=> (ex-info "Busted" {})))

(fact "that deleting an old launch configuration successfully gives the right response"
      (delete-old-launch-configuration {:parameters delete-old-launch-configuration-params}) => (contains {:status :success})
      (provided
       (aws/launch-configuration "old-lc" "environment" "region") => {}
       (aws/config anything anything) => {}
       (auto/delete-launch-configuration anything
                                         :launch-configuration-name "old-lc") => ..delete-result..))

(fact "that attempting to delete an old launch configuration for a deployment with no previous state is successful and does nothing"
      (delete-old-launch-configuration {:parameters (assoc delete-old-launch-configuration-params :previous-state nil)}) => (contains {:status :success}))

(fact "that attempting to scale down after deployment happens even if we haven't created anything"
      (scale-down-after-deployment {:parameters {:new-state {:tyranitar {:deployment-params {}}}}})
      => (contains {:status :success})
      (provided
       (auto/update-auto-scaling-group anything
                                       :auto-scaling-group-name "asg"
                                       :desired-capacity 0
                                       :max-size 0
                                       :min-size 0) => nil :times 0))

(fact "that attempting to scale down after deployment doesn't happen if we've not been told to"
      (scale-down-after-deployment {:parameters {:new-state {:auto-scaling-group-name "asg"
                                                             :created true
                                                             :tyranitar {:deployment-params {}}}}})
      => (contains {:status :success})
      (provided
       (auto/update-auto-scaling-group anything
                                       :auto-scaling-group-name "asg"
                                       :desired-capacity 0
                                       :max-size 0
                                       :min-size 0) => nil :times 0))

(fact "that attempting to scale down after deployment doesn't happen if we've not been told to"
      (scale-down-after-deployment {:parameters {:new-state {:auto-scaling-group-name "asg"
                                                             :created true
                                                             :tyranitar {:deployment-params {:scale-down-after-deployment false}}}}})
      => (contains {:status :success})
      (provided
       (auto/update-auto-scaling-group anything
                                       :auto-scaling-group-name "asg"
                                       :desired-capacity 0
                                       :max-size 0
                                       :min-size 0) => nil :times 0))

(fact "that attempting to scale down after deployment happens if we've been told to"
      (scale-down-after-deployment {:parameters {:new-state {:auto-scaling-group-name "asg"
                                                             :created true
                                                             :tyranitar {:deployment-params {:scale-down-after-deployment true}}}}})
      => (contains {:status :success})
      (provided
       (aws/config anything anything) => {}
       (auto/update-auto-scaling-group anything
                                       :auto-scaling-group-name "asg"
                                       :desired-capacity 0
                                       :max-size 0
                                       :min-size 0) => nil))

(fact "that an exception happening while attempting to scale down after deployment is an error"
      (scale-down-after-deployment {:parameters {:new-state {:auto-scaling-group-name "asg"
                                                             :created true
                                                             :tyranitar {:deployment-params {:scale-down-after-deployment true}}}}})
      => (contains {:status :error})
      (provided
       (aws/config anything anything) => {}
       (auto/update-auto-scaling-group anything
                                       :auto-scaling-group-name "asg"
                                       :desired-capacity 0
                                       :max-size 0
                                       :min-size 0) =throws=> (ex-info "Busted" {})))
