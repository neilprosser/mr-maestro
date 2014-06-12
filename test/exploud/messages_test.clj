(ns exploud.messages-test
  (:require [exploud
             [bindings :refer [*deployment-id*]]
             [deployments :as deployments]
             [elasticsearch :as es]
             [messages :refer :all]
             [redis :as redis]]
            [midje.sweet :refer :all]))

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
      (def params {:action :exploud.messages.health/something
                   :parameters {:new-state {:tyranitar {:deployment-params {:pause-after-instances-healthy true}}}}})
      (should-pause-because-of-deployment-params? params) => falsey)

(fact "that we should pause when the instances have been declared healthy and we're told we should"
      (def params {:action :exploud.messages.health/wait-for-instances-to-be-healthy
                   :parameters {:new-state {:tyranitar {:deployment-params {:pause-after-instances-healthy true}}}}})
      (should-pause-because-of-deployment-params? params) => truthy)

(fact "that we shouldn't pause when the instances have been declared healthy and we're told we shouldn't"
      (def params {:action :exploud.messages.health/wait-for-instances-to-be-healthy
                   :parameters {:new-state {:tyranitar {:deployment-params {:pause-after-instances-healthy false}}}}})
      (should-pause-because-of-deployment-params? params) => falsey)

(fact "that we shouldn't pause when the instances have been declared healthy and we've not been told anything"
      (def params {:action :exploud.messages.health/wait-for-instances-to-be-healthy
                   :parameters {:new-state {:tyranitar {:deployment-params {}}}}})
      (should-pause-because-of-deployment-params? params) => falsey)

(fact "that we should pause when the load balancers have been declared healthy and we're told we should"
      (def params {:action :exploud.messages.health/wait-for-load-balancers-to-be-healthy
                   :parameters {:new-state {:tyranitar {:deployment-params {:pause-after-load-balancers-healthy true}}}}})
      (should-pause-because-of-deployment-params? params) => truthy)

(fact "that we shouldn't pause when the load balancers have been declared healthy and we're told we shouldn't"
      (def params {:action :exploud.messages.health/wait-for-load-balancers-to-be-healthy
                   :parameters {:new-state {:tyranitar {:deployment-params {:pause-after-load-balancers-healthy false}}}}})
      (should-pause-because-of-deployment-params? params) => falsey)

(fact "that we shouldn't pause when the load balancers have been declared healthy and we've not been told anything"
      (def params {:action :exploud.messages.health/wait-for-load-balancers-to-be-healthy
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

(fact "that we enqueue the next task correctly"
      (def params {:message {:parameters ..parameters..}
                   :next-action :some-action
                   :result ..result..})
      (enqueue-next-task params) => params
      (provided
       (successful? ..result..) => true
       (should-pause? anything) => false
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
