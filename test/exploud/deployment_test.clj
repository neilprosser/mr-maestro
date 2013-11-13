(ns exploud.deployment_test
  (:require [clj-time.core :as time]
            [environ.core :refer [env]]
            [postal.core :as mail]
            [exploud
             [asgard :as asgard]
             [deployment :refer :all]
             [healthchecks :as health]
             [store :as store]
             [tyranitar :as tyr]
             [util :as util]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "the standard deployment tasks for an application are all there and in the correct order"
      (map :action (create-standard-deployment-tasks))
      => [:create-asg
          :wait-for-instance-health
          :enable-asg
          :wait-for-elb-health
          :disable-asg
          :delete-asg])

(fact "the standard deployment tasks all have a status of `pending`"
      (->> (create-standard-deployment-tasks)
           (map :status)
           (filter (fn [t] (= (:action t) "pending")))
           empty?))

(fact "creating the undo tasks for a deployment which has stopped at `:create-asg` creates a task which deletes that ASG"
      (create-undo-tasks [{:action :create-asg :status "failed"}
                          {:action :whatever :status "pending"}])
      => [{:action :create-asg :status "failed"}
          {:action :delete-asg :id ..id.. :status "pending" :undo true}]
      (provided
       (util/generate-id)
       => ..id..))

(fact "creating the undo tasks for a deployment which has stopped at `:wait-for-instance-health` creates nothing"
      (create-undo-tasks [{:action :wait-for-instance-health :status "failed"}])
      => [{:action :wait-for-instance-health :status "failed"}])

(fact "creating the undo tasks for a deployment which has stopped at `:enable-asg` creates a task which disables that ASG"
      (create-undo-tasks [{:action :enable-asg :status "failed"}])
      => [{:action :enable-asg :status "failed"}
          {:action :disable-asg :id ..id.. :status "pending" :undo true}]
      (provided
       (util/generate-id)
       => ..id..))

(fact "creating the undo tasks for a deployment which has stopped at `:wait-for-elb-health` creates nothing"
      (create-undo-tasks [{:action :wait-for-elb-health :status "failed"}])
      => [{:action :wait-for-elb-health :status "failed"}])

(fact "creating the undo tasks for a deployment which has stopped at `:disable-asg` creates tasks which enable that ASG and wait for the instances to be healthy in the ELB"
      (create-undo-tasks [{:action :disable-asg :status "failed"}])
      => [{:action :disable-asg :status "failed"}
          {:action :enable-asg :id ..id.. :status "pending" :undo true}
          {:action :wait-for-elb-health :id ..id.. :status "pending" :undo true}]
      (provided
       (util/generate-id)
       => ..id..))

(fact "creating the undo tasks for a deployment which has stopped at `:delete-asg` creates tasks which create that ASG and wait for it to be healthy"
      (create-undo-tasks [{:action :delete-asg :status "failed"}])
      => [{:action :delete-asg :status "failed"}
          {:action :create-asg :id ..id.. :status "pending" :undo true}
          {:action :wait-for-instance-health :id ..id.. :status "pending" :undo true}]
      (provided
       (util/generate-id)
       => ..id..))

(fact "creating the undo tasks for a realistic deployment which has stopped at `:delete-asg` creates the right tasks"
      (create-undo-tasks [{:action :create-asg :status "completed"}
                          {:action :wait-for-instance-health :status "completed"}
                          {:action :enable-asg :status "completed"}
                          {:action :wait-for-elb-health :status "skipped"}
                          {:action :disable-asg :status "completed"}
                          {:action :delete-asg :status "failed"}])
      => [{:action :create-asg :status "completed"}
          {:action :wait-for-instance-health :status "completed"}
          {:action :enable-asg :status "completed"}
          {:action :wait-for-elb-health :status "skipped"}
          {:action :disable-asg :status "completed"}
          {:action :delete-asg :status "failed"}
          {:action :create-asg :id ..id.. :status "pending" :undo true}
          {:action :wait-for-instance-health :id ..id.. :status "pending" :undo true}
          {:action :enable-asg :id ..id.. :status "pending" :undo true}
          {:action :wait-for-elb-health :id ..id.. :status "pending" :undo true}
          {:action :disable-asg :id ..id.. :status "pending" :undo true}
          {:action :delete-asg :id ..id.. :status "pending" :undo true}]
      (provided
       (util/generate-id)
       => ..id..))

(fact "that we obtain the properties for a deployment with no hash correctly and store the right things"
      (prepare-deployment ..region.. "app" ..env.. ..user.. ..ami.. nil ..message..)
      => {:ami ..ami..
          :application "app"
          :created ..created..
          :environment ..env..
          :hash ..hash..
          :id ..deploy-id..
          :message ..message..
          :parameters ..params..
          :region ..region..
          :tasks ..tasks..
          :user ..user..}
      (provided
       (asgard/image ..region.. ..ami..)
       => {:image {:name "ent-app-0.23"}}
       (tyr/last-commit-hash ..env.. "app")
       => ..hash..
       (tyr/deployment-params ..env.. "app" ..hash..)
       => ..params..
       (create-standard-deployment-tasks)
       => ..tasks..
       (util/generate-id)
       => ..deploy-id..
       (time/now)
       => ..created..
       (store/store-deployment {:ami ..ami..
                                :application "app"
                                :created ..created..
                                :environment ..env..
                                :hash ..hash..
                                :id ..deploy-id..
                                :message ..message..
                                :parameters ..params..
                                :region ..region..
                                :tasks ..tasks..
                                :user ..user..})
       => ..deploy-id..))

(fact "that we obtain the properties for a deployment with a hash correctly and store the right things"
      (prepare-deployment ..region.. "app" ..env.. ..user.. ..ami.. ..hash.. ..message..)
      => {:ami ..ami..
          :application "app"
          :created ..created..
          :environment ..env..
          :hash ..hash..
          :id ..deploy-id..
          :message ..message..
          :parameters ..params..
          :region ..region..
          :tasks ..tasks..
          :user ..user..}
      (provided
       (asgard/image ..region.. ..ami..)
       => {:image {:name "ent-app-0.23"}}
       (tyr/deployment-params ..env.. "app" ..hash..)
       => ..params..
       (create-standard-deployment-tasks)
       => ..tasks..
       (util/generate-id)
       => ..deploy-id..
       (time/now)
       => ..created..
       (store/store-deployment {:ami ..ami..
                                :application "app"
                                :created ..created..
                                :environment ..env..
                                :hash ..hash..
                                :id ..deploy-id..
                                :message ..message..
                                :parameters ..params..
                                :region ..region..
                                :tasks ..tasks..
                                :user ..user..})
       => ..deploy-id..))

(fact "that we get the details for a rollback and store the right things"
      (prepare-rollback ..region.. "app" ..env.. ..user.. ..message..)
      => {:ami ..old-ami..
          :application "app"
          :created ..created..
          :environment ..env..
          :hash ..old-hash..
          :id ..deploy-id..
          :message ..message..
          :parameters ..params..
          :region ..region..
          :tasks ..tasks..
          :user ..user..}
      (provided
       (store/get-completed-deployments {:application "app"
                                         :environment ..env..
                                         :size 1
                                         :from 1})
       => [{:ami ..old-ami..
            :application "app"
            :created ..old-created..
            :environment ..old-env..
            :hash ..old-hash..
            :id ..old-deploy-id..
            :message ..old-message..
            :parameters ..old-params..
            :region ..old-region..
            :tasks ..old-tasks..
            :user ..old-user..}]
       (asgard/image ..region.. ..old-ami..)
       => {:image {:name "ent-app-0.23"}}
       (tyr/deployment-params ..env.. "app" ..old-hash..)
       => ..params..
       (create-standard-deployment-tasks)
       => ..tasks..
       (util/generate-id)
       => ..deploy-id..
       (time/now)
       => ..created..
       (store/store-deployment {:ami ..old-ami..
                                :application "app"
                                :created ..created..
                                :environment ..env..
                                :hash ..old-hash..
                                :id ..deploy-id..
                                :message ..message..
                                :parameters ..params..
                                :region ..region..
                                :tasks ..tasks..
                                :user ..user..})
       => ..deploy-id..))

(fact "that we throw a wobbly when there is no penultimate completed deployment"
      (prepare-rollback ..region.. "app" ..env.. ..user.. ..message..)
      => (throws ExceptionInfo "No penultimate completed deployment to rollback to")
      (provided
       (store/get-completed-deployments {:application "app"
                                         :environment ..env..
                                         :size 1
                                         :from 1})
       => []))

(fact "that when we start the deployment we add a start date to the deployment and save it"
      (start-deployment ..deploy-id..)
      => nil
      (provided
       (store/get-deployment ..deploy-id..)
       => {:tasks [{:action ..action..}]}
       (time/now)
       => ..start..
       (store/store-deployment {:start ..start.. :tasks [{:action ..action..}]})
       => ..deploy-id..
       (start-task {:start ..start.. :tasks [{:action ..action..}]} {:action ..action..})
       => nil))

(fact "that starting a task with an action of `:create-asg` sets a `:start` value and calls Asgard correctly"
      (start-task {:ami ..ami..
                   :application ..app..
                   :environment ..env..
                   :id ..deploy-id..
                   :parameters ..params..
                   :region ..region..} {:action :create-asg})
      => ..create-result..
      (provided
       (time/now)
       => ..start..
       (asgard/create-auto-scaling-group {:ami ..ami..
                                          :application ..app..
                                          :environment ..env..
                                          :id ..deploy-id..
                                          :parameters ..params..
                                          :region ..region..
                                          :task {:action :create-asg :start ..start..}
                                          :completed-fn task-finished
                                          :timed-out-fn task-timed-out})
       => ..create-result..))

(fact "that completing a deployment adds an `:end` date to it"
      (finish-deployment {})
      => nil
      (provided
       (time/now)
       => ..end..
       (store/store-deployment {:end ..end..})
       => ..store-result..
       (send-completion-message anything)
       => anything))

(def deployment {:application "myapp"
                 :environment "test"
                 :user "auser"
                 :ami "ami-1234abcd"
                 :parameters {:notificationDestination "someone@example.com"}})

(fact "that messages are correctly formatted for sending."
      (send-completion-message deployment) => nil
      (provided
       (env :service-smtp-host) => "dummy-value"
       (env :service-mail-from) => "noreply@brislabs.com"
       (mail/send-message {:host "dummy-value"}
                          {:from "noreply@brislabs.com"
                           :to "someone@example.com"
                           :subject "Application Deployment Completed"
                           :body "Application myapp has been deployed to test by auser using ami-1234abcd."})
       => nil))


(fact "that finishing a task which is the last one in the deployment finishes that deployment"
      (task-finished ..deploy-id.. {:id ..task-id.. :status "completed"})
      => ..finish-result..
      (provided
       (time/now)
       => ..end..
       (store/store-task ..deploy-id.. {:id ..task-id..
                                        :end ..end..
                                        :status "completed"})
       => ..store-result..
       (store/get-deployment ..deploy-id..)
       => ..deployment..
       (task-after ..deployment.. ..task-id..)
       => nil
       (finish-deployment ..deployment..)
       => ..finish-result..))

(fact "that finishing a task which is not the last one in the deployment starts the next task"
      (task-finished ..deploy-id.. {:id ..task-id.. :status "completed"})
      => ..start-result..
      (provided
       (time/now)
       => ..end..
       (store/store-task ..deploy-id.. {:id ..task-id..
                                        :end ..end..
                                        :status "completed"})
       => ..store-result..
       (store/get-deployment ..deploy-id..)
       => ..deployment..
       (task-after ..deployment.. ..task-id..)
       => ..next-task..
       (start-task ..deployment.. ..next-task..)
       => ..start-result..))

(fact "that finishing a task which is skipped continues the deployment"
      (task-finished ..deploy-id.. {:id ..task-id.. :status "skipped"})
      => ..start-result..
      (provided
       (time/now)
       => ..end..
       (store/store-task ..deploy-id.. {:id ..task-id..
                                        :end ..end..
                                        :status "skipped"})
       => ..store-result..
       (store/get-deployment ..deploy-id..)
       => ..deployment..
       (task-after ..deployment.. ..task-id..)
       => ..next-task..
       (start-task ..deployment.. ..next-task..)
       => ..start-result..))

(fact "that finishing a task which is not completed halts the deployment"
      (task-finished ..deploy-id.. {:id ..task-id.. :status "failed"})
      => ..finish-result..
      (provided
       (time/now)
       => ..end..
       (store/store-task ..deploy-id.. {:id ..task-id..
                                        :end ..end..
                                        :status "failed"})
       => ..store-result..
       (store/get-deployment ..deploy-id..)
       => ..deployment..
       (finish-deployment ..deployment..)
       => ..finish-result..))

(fact "that getting the task after one that is last gives `nil`"
      (task-after {:tasks [{:id ..task-1..}
                           {:id ..task-2..}]}
                  ..task-2..)
      => nil)

(fact "that getting the task after one that is not last gives the correct task"
      (task-after {:tasks [{:id ..task-1..}
                           {:id ..task-2..}
                           {:id ..task-3..}]}
                  ..task-2..)
      => {:id ..task-3..})

(fact "that timing out a task puts an `:end` date and `:status` of `failed` on it"
      (task-timed-out ..deploy-id.. {:id ..task-id..})
      => nil
      (provided
       (time/now)
       => ..end..
       (store/store-task ..deploy-id.. {:id ..task-id..
                                        :end ..end..
                                        :status "failed"})
       => ..store-result..))

(fact "that starting a task with an action of `:enable-asg` sets a `:start` value and calls Asgard correctly"
      (start-task {:environment ..env..
                   :id ..deploy-id..
                   :parameters {:newAutoScalingGroupName ..new-asg..}
                   :region ..region..} {:action :enable-asg})
      => ..enable-result..
      (provided
       (time/now)
       => ..start..
       (asgard/enable-asg ..env.. ..region.. ..new-asg.. ..deploy-id.. {:action :enable-asg
                                                                :start ..start..} task-finished task-timed-out)
       => ..enable-result..))

(fact "that starting a task with an action of `:disable-asg` sets a `:start` value and calls Asgard correctly"
      (start-task {:environment ..env..
                   :id ..deploy-id..
                   :parameters {:oldAutoScalingGroupName ..old-asg..}
                   :region ..region..} {:action :disable-asg})
      => ..disable-result..
      (provided
       (time/now)
       => ..start..
       (asgard/disable-asg ..env.. ..region.. ..old-asg.. ..deploy-id.. {:action :disable-asg
                                                                 :start ..start..} task-finished task-timed-out)
       => ..disable-result..))

(fact "that starting a task with an action of `:delete-asg` sets a `:start` value and calls Asgard correctly"
      (start-task {:environment ..env..
                   :id ..deploy-id..
                   :parameters {:oldAutoScalingGroupName ..old-asg..}
                   :region ..region..} {:action :delete-asg})
      => ..delete-result..
      (provided
       (time/now)
       => ..start..
       (asgard/delete-asg ..env.. ..region.. ..old-asg.. ..deploy-id.. {:action :delete-asg
                                                                      :start ..start..} task-finished task-timed-out)
       => ..delete-result..))

(fact "that starting a task with an action of `:wait-for-instance-health` sets a `:start` value and skips the check when we've got zero as the `:min`"
      (start-task {:id ..deploy-id..
                   :parameters {:min 0}
                   :region ..region..
                   :environment ..env..
                   :application ..app..
                   :hash ..hash..} {:action :wait-for-instance-health})
      => ..completed-result..
      (provided
       (time/now)
       => ..start..
       (task-finished ..deploy-id.. {:log [{:message "Skipping instance healthcheck"
                                            :date ..start..}]
                                     :status "skipped"
                                     :action :wait-for-instance-health
                                     :start ..start..})
       => ..completed-result..))

(fact "that starting a task with an action of `:wait-for-instance-health` sets a `:start` value and does the right thing when we're using ELBs"
      (start-task {:id ..deploy-id..
                   :parameters {:min 1
                                :newAutoScalingGroupName ..new-asg..
                                :healthCheckType "ELB"
                                :selectedLoadBalancers ..elb..}
                   :region ..region..
                   :environment ..env..
                   :application ..app..
                   :hash ..hash..} {:action :wait-for-instance-health})
      => ..wait-result..
      (provided
       (time/now)
       => ..start..
       (tyr/application-properties ..env.. ..app.. ..hash..)
       => {:service.port 8082
           :service.healthcheck.path "/1.x/status"}
       (health/wait-until-asg-healthy ..env.. ..region.. ..new-asg.. 1 8082 "/1.x/status" ..deploy-id..
                                      {:action :wait-for-instance-health
                                       :start ..start..} task-finished task-timed-out)
       => ..wait-result..))

(fact "that starting a task with an action of `:wait-for-instance-health` sets a `:start` value and does the right thing when we're using ELBs"
      (start-task {:id ..deploy-id..
                   :parameters {:min 2
                                :newAutoScalingGroupName ..new-asg..
                                :healthCheckType "ELB"
                                :selectedLoadBalancers ..elb..}
                   :region ..region..
                   :environment ..env..
                   :application ..app..
                   :hash ..hash..} {:action :wait-for-instance-health})
      => ..wait-result..
      (provided
       (time/now)
       => ..start..
       (tyr/application-properties ..env.. ..app.. ..hash..)
       => {}
       (health/wait-until-asg-healthy ..env.. ..region.. ..new-asg.. 2 8080 "/healthcheck" ..deploy-id..
                                      {:action :wait-for-instance-health
                                       :start ..start..} task-finished task-timed-out)
       => ..wait-result..))

(fact "that starting a task with an action of `:wait-for-instance-health` sets a `:start` value and does the right thing when we have no `:min` in parameters"
      (start-task {:id ..deploy-id..
                   :parameters {:min nil
                                :newAutoScalingGroupName ..new-asg..
                                :healthCheckType "ELB"
                                :selectedLoadBalancers ..elb..}
                   :region ..region..
                   :environment ..env..
                   :application ..app..
                   :hash ..hash..} {:action :wait-for-instance-health})
      => ..wait-result..
      (provided
       (time/now)
       => ..start..
       (tyr/application-properties ..env.. ..app.. ..hash..)
       => {}
       (health/wait-until-asg-healthy ..env.. ..region.. ..new-asg.. 1 8080 "/healthcheck" ..deploy-id..
                                      {:action :wait-for-instance-health
                                       :start ..start..} task-finished task-timed-out)
       => ..wait-result..))

(fact "that when deciding whether we should check instance health we return true when we have no `:min`"
      (wait-for-instance-health? {:parameters {}})
      => true)

(fact "that when deciding whether we should check instance health we return true when we have a nil value of `:min`"
      (wait-for-instance-health? {:parameters {:min nil}})
      => true)

(fact "that when deciding whether we should check instance health we return true when we have a positive value for `:min`"
      (wait-for-instance-health? {:parameters {:min 1}})
      => true)

(fact "that when deciding whether to check ELB health we return true when we have selectedLoadBalancers and a healthCheckType of ELB"
      (check-elb-health? {:parameters {:healthCheckType "ELB"
                                       :selectedLoadBalancers "elb"}})
      => true)

(fact "that when deciding whether to check ELB health we return true when we have multiple selectedLoadBalancers and a healthCheckType of ELB"
      (check-elb-health? {:parameters {:healthCheckType "ELB"
                                       :selectedLoadBalancers ["elb1" "elb2"]}})
      => true)

(fact "that when deciding whether to check ELB health we return false when we have empty selectedLoadBalancers and a healthCheckType of ELB"
      (check-elb-health? {:parameters {:healthCheckType "ELB"
                                       :selectedLoadBalancers []}})
      => false)

(fact "that when deciding whether to check ELB health we return false when we have selectedLoadBalancers but not a healthCheckType of ELB"
      (check-elb-health? {:parameters {:healthCheckType "EC2"
                                       :selectedLoadBalancers "elb"}})
      => false)

(fact "that when deciding whether to check ELB health we return false when we don't have selectedLoadBalancers but we do have a healthCheckType of ELB"
      (check-elb-health? {:parameters {:healthCheckType "ELB"}})
      => false)

(fact "that starting a task with an action of `:wait-for-elb-health` sets a `:start` value and does the right when using a single ELB"
      (start-task {:environment ..env..
                   :id ..deploy-id..
                   :parameters {:newAutoScalingGroupName ..new-asg..
                                :healthCheckType "ELB"
                                :selectedLoadBalancers "elb"}
                   :region ..region..} {:action :wait-for-elb-health})
      => ..wait-result..
      (provided
       (time/now)
       => ..start..
       (health/wait-until-elb-healthy ..env.. ..region.. ["elb"] ..new-asg..
                                      ..deploy-id.. {:action :wait-for-elb-health
                                                     :start ..start..} task-finished task-timed-out)
       => ..wait-result..))

(fact "that starting a task with an action of `:wait-for-elb-health` sets a `:start` value and does the right when using a multiple ELBs"
      (start-task {:environment ..env..
                   :id ..deploy-id..
                   :parameters {:newAutoScalingGroupName ..new-asg..
                                :healthCheckType "ELB"
                                :selectedLoadBalancers ["elb1"
                                                        "elb2"]}
                   :region ..region..} {:action :wait-for-elb-health})
      => ..wait-result..
      (provided
       (time/now)
       => ..start..
       (health/wait-until-elb-healthy ..env.. ..region.. ["elb1" "elb2"] ..new-asg..
                                      ..deploy-id.. {:action :wait-for-elb-health
                                                     :start ..start..} task-finished task-timed-out)
       => ..wait-result..))

(fact "that starting a task with an action of `:wait-for-elb-health` sets a `:start` value and then finishes the task when we're not using an ELB healthCheckType"
      (start-task {:id ..deploy-id..
                   :parameters {:newAutoScalingGroupName ..new-asg..
                                :healthCheckType "EC2"
                                :selectedLoadBalancers "elb"}
                   :region ..region..} {:action :wait-for-elb-health})
      => ..finish-result..
      (provided
       (time/now)
       => ..start..
       (task-finished ..deploy-id.. {:log [{:message "Skipping ELB healthcheck"
                                            :date ..start..}]
                                     :status "skipped"
                                     :action :wait-for-elb-health
                                     :start ..start..})
       => ..finish-result..))

(fact "that starting a task with an action of `:wait-for-elb-health` sets a `:start` value and then finishes the task when we've not provided any selectedLoadBalancers"
      (start-task {:id ..deploy-id..
                   :parameters {:newAutoScalingGroupName ..new-asg..
                                :healthCheckType "ELB"}
                   :region ..region..} {:action :wait-for-elb-health})
      => ..finish-result..
      (provided
       (time/now)
       => ..start..
       (task-finished ..deploy-id.. {:log [{:message "Skipping ELB healthcheck"
                                            :date ..start..}]
                                     :status "skipped"
                                     :action :wait-for-elb-health
                                     :start ..start..})
       => ..finish-result..))

(fact "that starting a task with an action of `:wait-for-elb-health` sets a `:start` value and then finishes the task when we've not provided any selectedLoadBalancers or a healthCheckType of ELB"
      (start-task {:id ..deploy-id..
                   :parameters {:newAutoScalingGroupName ..new-asg..}
                   :region ..region..} {:action :wait-for-elb-health})
      => ..finish-result..
      (provided
       (time/now)
       => ..start..
       (task-finished ..deploy-id.. {:log [{:message "Skipping ELB healthcheck"
                                            :date ..start..}]
                                     :status "skipped"
                                     :action :wait-for-elb-health
                                     :start ..start..})
       => ..finish-result..))

(fact "that preparing a deployment and providing an AMI which doesn't match the application being deployed throws a wobbly"
      (prepare-deployment "region" "application" "environment" "user" "ami" nil "message")
      => (throws ExceptionInfo "Image does not match application")
      (provided
       (asgard/image "region" "ami")
       => {:image {:name "ent-somethingelse-0.12"}}))

(fact "that preparing an undo for a deployment with no tasks gives back the deployment"
      (prepare-undo {:tasks []})
      => {:tasks []})

(fact "that preparing an undo for a deployment with tasks does the right thing"
      (prepare-undo {:tasks [..task..]})
      => nil
      (provided
       (create-undo-tasks [..task..])
       => ..edited-tasks..
       (store/store-deployment {:tasks ..edited-tasks..})
       => ..store-result..))
