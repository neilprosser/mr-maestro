(ns exploud.deployment_test
  (:require [exploud
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

(fact "that we obtain the properties for a deployment correctly and store the right things"
      (prepare-deployment ..region.. ..app.. ..env.. ..user.. ..ami..)
      => {:ami ..ami..
          :application ..app..
          :created ..created..
          :environment ..env..
          :hash ..hash..
          :id ..deploy-id..
          :parameters ..params..
          :region ..region..
          :tasks ..tasks..
          :user ..user..}
      (provided
       (tyr/last-commit-hash ..env.. ..app..)
       => ..hash..
       (tyr/deployment-params ..env.. ..app.. ..hash..)
       => ..params..
       (create-standard-deployment-tasks)
       => ..tasks..
       (util/generate-id)
       => ..deploy-id..
       (util/now-string)
       => ..created..
       (store/store-deployment {:ami ..ami..
                                :application ..app..
                                :created ..created..
                                :environment ..env..
                                :hash ..hash..
                                :id ..deploy-id..
                                :parameters ..params..
                                :region ..region..
                                :tasks ..tasks..
                                :user ..user..})
       => ..deploy-id..))

(fact "that when we start the deployment we add a start date to the deployment and save it"
      (start-deployment ..deploy-id..)
      => nil
      (provided
       (store/get-deployment ..deploy-id..)
       => {:tasks [{:action ..action..}]}
       (util/now-string)
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
       (util/now-string)
       => ..start..
       (asgard/create-auto-scaling-group ..region.. ..app.. ..env.. ..ami.. ..params.. ..deploy-id.. {:action :create-asg :start ..start..} task-finished task-timed-out)
       => ..create-result..))

(fact "that completing a deployment adds an `:end` date to it"
      (finish-deployment {})
      => nil
      (provided
       (util/now-string)
       => ..end..
       (store/store-deployment {:end ..end..})
       => ..store-result..))

(fact "that finishing a task which is the last one in the deployment finishes that deployment"
      (task-finished ..deploy-id.. {:id ..task-id..})
      => ..finish-result..
      (provided
       (util/now-string)
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
      (task-finished ..deploy-id.. {:id ..task-id..})
      => ..start-result..
      (provided
       (util/now-string)
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

(fact "that attempting to start a task with an unrecognised `:action` throws up"
      (start-task ..deployment.. {:action :unrecognised})
      => (throws ExceptionInfo "Unrecognised action."))

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
       (util/now-string)
       => ..end..
       (store/store-task ..deploy-id.. {:id ..task-id..
                                        :end ..end..
                                        :status "failed"})
       => ..store-result..))

(fact "that starting a task with an action of `:enable-asg` sets a `:start` value and calls Asgard correctly"
      (start-task {:id ..deploy-id..
                   :parameters {:newAutoScalingGroupName ..new-asg..}
                   :region ..region..} {:action :enable-asg})
      => ..enable-result..
      (provided
       (util/now-string)
       => ..start..
       (asgard/enable-asg ..region.. ..new-asg.. ..deploy-id.. {:action :enable-asg
                                                                :start ..start..} task-finished task-timed-out)
       => ..enable-result..))

(fact "that starting a task with an action of `:disable-asg` sets a `:start` value and calls Asgard correctly"
      (start-task {:id ..deploy-id..
                   :parameters {:oldAutoScalingGroupName ..old-asg..}
                   :region ..region..} {:action :disable-asg})
      => ..disable-result..
      (provided
       (util/now-string)
       => ..start..
       (asgard/disable-asg ..region.. ..old-asg.. ..deploy-id.. {:action :disable-asg
                                                                 :start ..start..} task-finished task-timed-out)
       => ..disable-result..))

(fact "that starting a task with an action of `:delete-asg` sets a `:start` value and calls Asgard correctly"
      (start-task {:id ..deploy-id..
                   :parameters {:oldAutoScalingGroupName ..old-asg..}
                   :region ..region..} {:action :delete-asg})
      => ..delete-result..
      (provided
       (util/now-string)
       => ..start..
       (asgard/delete-asg ..region.. ..old-asg.. ..deploy-id.. {:action :delete-asg
                                                                :start ..start..} task-finished task-timed-out)
       => ..delete-result..))

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
       (util/now-string)
       => ..start..
       (tyr/application-properties ..env.. ..app.. ..hash..)
       => {:service.port 8082
           :service.healthcheck.path "/1.x/status"}
       (health/wait-until-asg-healthy ..region.. ..new-asg.. 1 8082 "/1.x/status" ..deploy-id..
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
       (util/now-string)
       => ..start..
       (tyr/application-properties ..env.. ..app.. ..hash..)
       => {}
       (health/wait-until-asg-healthy ..region.. ..new-asg.. 2 8080 "/healthcheck" ..deploy-id..
                                      {:action :wait-for-instance-health
                                       :start ..start..} task-finished task-timed-out)
       => ..wait-result..))

(fact "that when deciding whether we should check instance health we return false when we have no `:min`"
      (wait-for-instance-health? {:parameters {}})
      => false)

(fact "that when deciding whether we should check instance health we return false when we have a nil value of `:min`"
      (wait-for-instance-health? {:parameters {:min nil}})
      => false)

(fact "that when deciding whether we should check instance health we return true when we have a positive value for `:min`"
      (wait-for-instance-health? {:parameters {:min 1}}))

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
      (start-task {:id ..deploy-id..
                   :parameters {:newAutoScalingGroupName ..new-asg..
                                :healthCheckType "ELB"
                                :selectedLoadBalancers "elb"}
                   :region ..region..} {:action :wait-for-elb-health})
      => ..wait-result..
      (provided
       (util/now-string)
       => ..start..
       (health/wait-until-elb-healthy ..region.. ["elb"] ..new-asg..
                                      ..deploy-id.. {:action :wait-for-elb-health
                                                     :start ..start..} task-finished task-timed-out)
       => ..wait-result..))

(fact "that starting a task with an action of `:wait-for-elb-health` sets a `:start` value and does the right when using a multiple ELBs"
      (start-task {:id ..deploy-id..
                   :parameters {:newAutoScalingGroupName ..new-asg..
                                :healthCheckType "ELB"
                                :selectedLoadBalancers ["elb1"
                                                        "elb2"]}
                   :region ..region..} {:action :wait-for-elb-health})
      => ..wait-result..
      (provided
       (util/now-string)
       => ..start..
       (health/wait-until-elb-healthy ..region.. ["elb1" "elb2"] ..new-asg..
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
       (util/now-string)
       => ..start..
       (task-finished ..deploy-id.. {:action :wait-for-elb-health
                                     :start ..start..})
       => ..finish-result..))

(fact "that starting a task with an action of `:wait-for-elb-health` sets a `:start` value and then finishes the task when we've not provided any selectedLoadBalancers"
      (start-task {:id ..deploy-id..
                   :parameters {:newAutoScalingGroupName ..new-asg..
                                :healthCheckType "ELB"}
                   :region ..region..} {:action :wait-for-elb-health})
      => ..finish-result..
      (provided
       (util/now-string)
       => ..start..
       (task-finished ..deploy-id.. {:action :wait-for-elb-health
                                     :start ..start..})
       => ..finish-result..))

(fact "that starting a task with an action of `:wait-for-elb-health` sets a `:start` value and then finishes the task when we've not provided any selectedLoadBalancers or a healthCheckType of "
      (start-task {:id ..deploy-id..
                   :parameters {:newAutoScalingGroupName ..new-asg..}
                   :region ..region..} {:action :wait-for-elb-health})
      => ..finish-result..
      (provided
       (util/now-string)
       => ..start..
       (task-finished ..deploy-id.. {:action :wait-for-elb-health
                                     :start ..start..})
       => ..finish-result..))
