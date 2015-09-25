(ns maestro.messages-test
  (:require [clj-time.core :as time]
            [maestro
             [actions :as actions]
             [bindings :refer :all]
             [deployments :as deployments]
             [elasticsearch :as es]
             [log :as log]
             [messages :refer :all]
             [redis :as redis]
             [responses :refer :all]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "that rewrapping the message does what we want"
      (binding [*deployment-id* ..deployment-id..]
        (rewrap {:mid ..mid.. :message {:parameters {:something "woo"}} :attempt ..attempt..})
        => {:id ..mid..
            :parameters {:id ..deployment-id..
                         :something "woo"
                         :status "running"}
            :attempt ..attempt..}))

(fact "that determining whether something is terminal works when it has an error status"
      (terminal? {:status :error}) => true)

(fact "that determining whether something is terminal works when it has a success status"
      (terminal? {:status :success}) => true)

(fact "that determining whether something is terminal works when it has a different status"
      (terminal? {:status :whatever}) => false)

(fact "that determining whether something is successful works when it has a success status"
      (successful? {:status :success}) => true)

(fact "that determining whether something is successful works when it has a different status"
      (successful? {:status :whatever}) => false)

(fact "that a successful task has a completed status"
      (task-status-for {:status :success}) => "completed")

(fact "that an errored task has a failed status"
      (task-status-for {:status :error}) => "failed")

(fact "that a non-successful or errored task has a running status"
      (task-status-for {:status :whatever}) => "running")

(fact "that an errored deployment has a failed status"
      (deployment-status-for {:status :error}) => "failed")

(fact "that a non-errored deployment has a running status"
      (deployment-status-for {:status :whatever}) => "running")

(fact "that ensuring a task which has already been attempted does nothing"
      (ensure-task {:attempt 2}) => {:attempt 2}
      (provided
       (es/create-task anything anything anything) => nil :times 0))

(fact "that ensuring a task which is on its first attempt creates the task"
      (binding [*deployment-id* "deployment-id"
                *task-id* "task-id"]
        (def details {:attempt 1
                      :message {:action ..action..}})
        (ensure-task details) => details
        (provided
         (actions/sequence-number ..action..) => ..sequence..
         (time/now) => ..now..
         (es/create-task "task-id" "deployment-id" {:action ..action..
                                                    :sequence ..sequence..
                                                    :start ..now..
                                                    :status "running"}) => ..create-resut..)))

(fact "that we use the result from the action is it's there"
      (binding [*deployment-id* "deployment-id"]
        (perform-action (fn [x] (success (:parameters x))) {:attempt 1
                                                           :message {:parameters {:some "parameters"}}
                                                           :mid "message-id"})
        => {:attempt 1
            :message {:parameters {:some "parameters"}}
            :mid "message-id"
            :result {:parameters {:id "deployment-id"
                                  :some "parameters"
                                  :status "running"}
                     :status :success}}))

(fact "that we assume success if nothing is returned from the action function"
      (binding [*deployment-id* "deployment-id"]
        (perform-action (fn [x] nil) {:attempt 1
                                     :message {:parameters {:some "parameters"}}
                                     :mid "message-id"})
        => {:attempt 1
            :message {:parameters {:some "parameters"}}
            :mid "message-id"
            :result {:parameters {:id "deployment-id"
                                  :some "parameters"
                                  :status "running"}
                     :status :success}}))

(fact "that an uncaught exception while performing an action results in an error"
      (def exception (ex-info "Busted" {}))
      (perform-action (fn [x] (throw exception)) {:attempt 1
                                                 :message {:parameters {:some "parameters"}}
                                                 :mid "message-id"})
      => {:attempt 1
          :message {:parameters {:some "parameters"}}
          :mid "message-id"
          :result {:status :error
                   :throwable exception}})

(fact "that nothing happens when attempting to log an error for a successful result"
      (def details {:result {:status :success}})
      (log-error-if-necessary details) => details
      (provided
       (log/write anything) => nil :times 0))

(fact "that we write the message from a throwable if present"
      (def details {:result {:status :error
                             :throwable (ex-info "Busted" {})}})
      (log-error-if-necessary details) => details
      (provided
       (log/write "Busted") => nil))

(fact "that we write a generic message if no throwable is present"
      (def details {:result {:status :error}})
      (log-error-if-necessary details) => details
      (provided
       (log/write "An unspecified error has occurred. It might be worth checking Maestro's logs.") => nil))

(fact "that we can determine the next action correctly"
      (determine-next-action {:message {:action ..action..}})
      => {:message {:action ..action..}
          :next-action ..next-action..}
      (provided
       (actions/action-after ..action..) => ..next-action..))

(fact "that updating a task when the result isn't terminal does nothing"
      (update-task {:result ..result..})
      => {:result ..result..}
      (provided
       (terminal? ..result..) => false
       (es/update-task anything anything anything) => nil :times 0))

(fact "that updating a task when the result is terminal calls Elasticsearch"
      (binding [*deployment-id* "deployment-id"
                *task-id* "task-id"]
        (update-task {:result ..result..})
        => {:result ..result..}
        (provided
         (terminal? ..result..) => true
         (task-status-for ..result..) => ..status..
         (time/now) => ..now..
         (es/update-task "task-id" "deployment-id" {:end ..now..
                                                    :status ..status..}) => ..update-result..)))

(fact "that a deployment in the preparation phase gets a failure status of invalid"
      (failure-status {:message {:parameters {:phase "preparation"}}}) => "invalid")

(fact "that a deployment in any other phase gets a failure status of failed"
      (failure-status {:message {:parameters {:phase "anything"}}}) => "failed")

(fact "that updating a deployment after a successful task, with another to follow, does the right thing"
      (binding [*deployment-id* "deployment-id"]
        (update-deployment {:message {:parameters {:old "parameters"}}
                            :next-action ..next-action..
                            :result {:parameters {:new "parameters"}
                                     :status :success}})
        => {:message {:parameters {:new "parameters"
                                   :status "running"}}
            :next-action ..next-action..
            :result {:parameters {:new "parameters"}
                     :status :success}}
        (provided
         (es/upsert-deployment "deployment-id" {:new "parameters"
                                                :status "running"}) => ..upsert-result..)))

(fact "that updating a deployment after a successful task, with no another to follow, does the right thing"
      (binding [*deployment-id* "deployment-id"]
        (update-deployment {:message {:parameters {:old "parameters"}}
                            :result {:parameters {:new "parameters"}
                                     :status :success}})
        => {:message {:parameters {:new "parameters"
                                   :status "completed"}}
            :result {:parameters {:new "parameters"}
                     :status :success}}
        (provided
         (es/upsert-deployment "deployment-id" {:new "parameters"
                                                :status "completed"}) => ..upsert-result..)))

(fact "that updating a deployment after a failed task does the right thing"
      (binding [*deployment-id* "deployment-id"]
        (update-deployment {:message {:parameters {:old "parameters"}}
                            :result {:status :error}})
        => {:message {:parameters {:end ..now..
                                   :old "parameters"
                                   :status "failed"}}
            :result {:status :error}}
        (provided
         (time/now) => ..now..
         (es/upsert-deployment "deployment-id" {:end ..now..
                                                :old "parameters"
                                                :status "failed"}) => ..upsert-result..)))

(fact "that updating a deployment after a task which needs to retry does nothing"
      (update-deployment {:result {:status :retry}})
      => {:result {:status :retry}}
      (provided
       (es/upsert-deployment anything anything) => nil :times 0))

(fact "that we should pause when the deployment has a pause registered"
      (def params {:parameters {:application "application"
                                :environment "environment"
                                :region "region"}})
      (should-pause-because-told-to? params) => truthy
      (provided
       (deployments/pause-registered? {:application "application"
                                       :environment "environment"
                                       :region "region"}) => true))

(fact "that we shouldn't pause when the deployment doesn't have a pause registered"
      (def params {:parameters {:application "application"
                                :environment "environment"
                                :region "region"}})
      (should-pause-because-told-to? params) => falsey
      (provided
       (deployments/pause-registered? {:application "application"
                                       :environment "environment"
                                       :region "region"}) => false))

(fact "that we shouldn't pause for a random action"
      (def params {:action :maestro.messages.health/something
                   :parameters {:new-state {:tyranitar {:deployment-params {:pause-after-instances-healthy true}}}}})
      (should-pause-because-of-deployment-params? params) => falsey)

(fact "that we should pause when the instances have been declared healthy and we're told we should"
      (def params {:action :maestro.messages.health/wait-for-instances-to-be-healthy
                   :parameters {:new-state {:tyranitar {:deployment-params {:pause-after-instances-healthy true}}}}})
      (should-pause-because-of-deployment-params? params) => truthy)

(fact "that we shouldn't pause when the instances have been declared healthy and we're told we shouldn't"
      (def params {:action :maestro.messages.health/wait-for-instances-to-be-healthy
                   :parameters {:new-state {:tyranitar {:deployment-params {:pause-after-instances-healthy false}}}}})
      (should-pause-because-of-deployment-params? params) => falsey)

(fact "that we shouldn't pause when the instances have been declared healthy and we've not been told anything"
      (def params {:action :maestro.messages.health/wait-for-instances-to-be-healthy
                   :parameters {:new-state {:tyranitar {:deployment-params {}}}}})
      (should-pause-because-of-deployment-params? params) => falsey)

(fact "that we should pause when the load balancers have been declared healthy and we're told we should"
      (def params {:action :maestro.messages.health/wait-for-load-balancers-to-be-healthy
                   :parameters {:new-state {:tyranitar {:deployment-params {:pause-after-load-balancers-healthy true}}}}})
      (should-pause-because-of-deployment-params? params) => truthy)

(fact "that we shouldn't pause when the load balancers have been declared healthy and we're told we shouldn't"
      (def params {:action :maestro.messages.health/wait-for-load-balancers-to-be-healthy
                   :parameters {:new-state {:tyranitar {:deployment-params {:pause-after-load-balancers-healthy false}}}}})
      (should-pause-because-of-deployment-params? params) => falsey)

(fact "that we should pause when the old instances have been removed from the load balancer and we're told we should"
      (def params {:action :maestro.messages.asg/deregister-old-instances-from-load-balancers
                   :parameters {:new-state {:tyranitar {:deployment-params {:pause-after-deregister-old-instances true}}}}})
      (should-pause-because-of-deployment-params? params) => truthy)

(fact "that we shouldn't pause when the instances have been removed from the load balancer and we're told we shouldn't"
      (def params {:action :maestro.messages.asg/deregister-old-instances-from-load-balancers
                   :parameters {:new-state {:tyranitar {:deployment-params {:pause-after-deregister-old-instances false}}}}})
      (should-pause-because-of-deployment-params? params) => falsey)

(fact "that we shouldn't pause when the load balancers have been declared healthy and we've not been told anything"
      (def params {:action :maestro.messages.health/wait-for-load-balancers-to-be-healthy
                   :parameters {:new-state {:tyranitar {:deployment-params {}}}}})
      (should-pause-because-of-deployment-params? params) => falsey)

(fact "that we should pause if we're told to"
      (should-pause? {}) => true
      (provided
       (should-pause-because-told-to? anything) => true))

(fact "that we should pause because the deployment params tell us to"
      (should-pause? {}) => true
      (provided
       (should-pause-because-told-to? anything) => false
       (should-pause-because-of-deployment-params? anything) => true))

(fact "that we shouldn't pause if we're not told to or the deployment params don't let us"
      (should-pause? {}) => false
      (provided
       (should-pause-because-told-to? anything) => false
       (should-pause-because-of-deployment-params? anything) => false))

(fact "that we shouldn't cancel if we're not retrying the action"
      (should-cancel? {:message ..message.. :result {:status :success}}) => false)

(fact "that we shouldn't cancel if there's no cancel registered"
      (should-cancel? {:message {:parameters ..parameters..} :result {:status :retry}}) => false
      (provided
       (deployments/cancel-registered? ..parameters..) => false))

(fact "that we should cancel if we're retrying and there's a cancel registered"
      (should-cancel? {:message {:parameters ..parameters..} :result {:status :retry}}) => true
      (provided
       (deployments/cancel-registered? ..parameters..) => true))

(fact "that we enqueue the next task correctly"
      (def params {:message {:parameters ..parameters..}
                   :next-action :some-action
                   :result ..result..})
      (enqueue-next-task params) => params
      (provided
       (successful? ..result..) => true
       (should-pause? anything) => false
       (should-cancel? params) => false
       (redis/enqueue {:action :some-action
                       :parameters ..parameters..}) => ..enqueue..))

(fact "that we don't enqueue the next task if there isn't one"
      (def params {:message {:parameters ..parameters..}
                   :result ..result..})
      (enqueue-next-task params) => params
      (provided
       (successful? ..result..) => true
       (redis/enqueue anything) => ..enqueue.. :times 0))

(fact "that we don't enqueue the next task if we've been unsuccessful"
      (def params {:message {:parameters ..parameters..}
                   :next-action :some-action
                   :result ..result..})
      (enqueue-next-task params) => params
      (provided
       (successful? ..result..) => false
       (redis/enqueue anything) => ..enqueue.. :times 0))

(fact "that we don't enqueue the next task if we should pause"
      (def params {:message {:parameters ..parameters..}
                   :next-action :some-action
                   :result ..result..})
      (enqueue-next-task params) => params
      (provided
       (successful? ..result..) => true
       (should-pause? {:parameters ..parameters..}) => true
       (deployments/pause ..parameters..) => ..pause..
       (redis/enqueue anything) => ..enqueue.. :times 0))

(fact "that we don't enqueue the next task if we should cancel"
      (def params {:message {:parameters ..parameters..}
                   :next-action :some-action
                   :result {:status :success}})
      (enqueue-next-task params) => {:message {:parameters ..parameters..}
                                     :next-action :some-action
                                     :result {:status :error}}
      (provided
       (successful? {:status :success}) => true
       (should-pause? {:parameters ..parameters..}) => false
       (should-cancel? params) => true
       (deployments/cancel ..parameters..) => ..cancel..
       (redis/enqueue anything) => ..enqueue.. :times 0))

(fact "that we're finishing if there's no next action"
      (finishing? {}) => true)

(fact "that we're not finishing if there's a next action"
      (finishing? {:next-action :something}) => false)

(fact "that we've safely failed if the status is invalid"
      (safely-failed? {:message {:parameters {:status "invalid"}}}))

(fact "that we've not safely failed if the status isn't invalid"
      (safely-failed? {:message {:parameters {:status "not-invalid"}}}))

(fact "that we end the deployment if we're finishing"
      (end-deployment-if-allowed {}) => {}
      (provided
       (finishing? anything) => true
       (deployments/end anything) => :whatever))

(fact "that we end the deployment if we've safely failed"
      (end-deployment-if-allowed {}) => {}
      (provided
       (finishing? anything) => false
       (safely-failed? anything) => true
       (deployments/end anything) => :whatever))

(fact "that we don't end the deployment if we're not finishing and we haven't safely failed"
      (end-deployment-if-allowed {}) => {}
      (provided
       (finishing? anything) => false
       (safely-failed? anything) => false
       (deployments/end anything) => :whatever :times 0))

(fact "that our handler will throw up when no deployment ID is provided"
      (handler {:attempt 1
                :message {:action "action"
                          :parameters {:id nil}}
                :mid "message-id"})
      => (throws ExceptionInfo "No deployment ID provided"))

(fact "that our handler will perform a capped retry on an unknown action"
      (handler {:attempt 1
                :message {:action "action"
                          :parameters {:id "deployment-id"}}
                :mid "message-id"})
      => {:backoff-ms 5000
          :status :retry}
      (provided
       (log/write "Unknown action.") => nil
       (actions/to-function "action") => nil))

(fact "that our handler goes through all the functions in the correct order"
      (handler {:attempt 1
                :message {:action "action"
                          :parameters {:id "deployment-id"}}
                :mid "message-id"})
      => ..result..
      (provided
       (actions/to-function "action") => ..action..
       (ensure-task {:attempt 1
                     :message {:action "action"
                               :parameters {:id "deployment-id"}}
                     :mid "message-id"})
       => ..after-ensure..
       (perform-action ..action.. ..after-ensure..) => ..after-perform..
       (log-error-if-necessary ..after-perform..) => ..after-log..
       (determine-next-action ..after-log..) => ..after-determine..
       (update-task ..after-determine..) => ..after-update-task..
       (update-deployment ..after-update-task..) => ..after-update-deployment..
       (enqueue-next-task ..after-update-deployment..) => ..after-enqueue..
       (end-deployment-if-allowed ..after-enqueue..) => {:result ..result..}))
