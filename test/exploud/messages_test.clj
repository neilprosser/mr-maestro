(ns exploud.messages-test
  (:require [exploud
             [deployments :as deployments]
             [elasticsearch :as es]
             [messages :refer :all]
             [tasks :as tasks]]
            [midje.sweet :refer :all]))

(fact "that we should pause when the deployment has a pause registered"
      (def params {:parameters {:application "application"
                                :environment "environment"
                                :region "region"}})
      (should-pause-because-told-to? params) => truthy
      (provided
       (deployments/pause-registered? params) => true))

(fact "that we shouldn't pause when the deployment doesn't have a pause registered"
      (def params {:parameters {:application "application"
                                :environment "environment"
                                :region "region"}})
      (should-pause-because-told-to? params) => falsey
      (provided
       (deployments/pause-registered? params) => false))

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
       (tasks/enqueue {:action :some-action
                       :parameters ..parameters..}) => ..enqueue..))

(fact "that we don't enqueue the next task if there isn't one"
      (def params {:message {:parameters ..parameters..}
                   :result ..result..})
      (enqueue-next-task params) => params
      (provided
       (successful? ..result..) => true
       (tasks/enqueue anything) => ..enqueue.. :times 0))

(fact "that we don't enqueue the next task if we've been unsuccessful"
      (def params {:message {:parameters ..parameters..}
                   :next-action :some-action
                   :result ..result..})
      (enqueue-next-task params) => params
      (provided
       (successful? ..result..) => false
       (tasks/enqueue anything) => ..enqueue.. :times 0))

(fact "that we don't enqueue the next task if we should pause"
      (def params {:message {:parameters ..parameters..}
                   :next-action :some-action
                   :result ..result..})
      (enqueue-next-task params) => params
      (provided
       (successful? ..result..) => true
       (should-pause? {:parameters ..parameters..}) => true
       (deployments/pause ..parameters..) => ..pause..
       (tasks/enqueue anything) => ..enqueue.. :times 0))

(fact "that we're finishing if there's no next action"
      (finishing? {}) => true)

(fact "that we're not finishing if there's a next action"
      (finishing? {:next-action :something}) => false)

(fact "that we've safely failed if the status is invalid"
      (safely-failed? {:message {:parameters {:status "invalid"}}}))

(fact "that we've not safely failed if the status isn't invalid"
      (safely-failed? {:message {:parameters {:status "not-invalid"}}}))
