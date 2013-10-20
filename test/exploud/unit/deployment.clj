(ns exploud.unit.deployment
  (:require [exploud
             [asgard_new :as asgard]
             [deployment :refer :all]
             [store :as store]
             [tyranitar :as tyr]
             [util :as util]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "the standard deployment tasks for an application are all there and in the correct order"
      (map :action (create-standard-deployment-tasks))
      => [:create-asg
          :wait-for-health
          :enable-asg
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
       (start-task {:action ..action..})
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
                                        :end ..end..})
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
                                        :end ..end..})
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

(fact "that timing out a task puts an `:end` date on it"
      (task-timed-out ..deploy-id.. {:id ..task-id..})
      => nil
      (provided
       (util/now-string)
       => ..end..
       (store/store-task ..deploy-id.. {:id ..task-id..
                                        :end ..end..})
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
