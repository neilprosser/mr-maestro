(ns exploud.messages-test
  (:require [exploud
             [deployments :as deployments]
             [elasticsearch :as es]
             [messages :refer :all]
             [tasks :as tasks]]
            [midje.sweet :refer :all]))

(fact "that we enqueue the next task correctly"
      (def params {:message {:parameters ..parameters..}
                   :next-action :some-action
                   :result ..result..})
      (enqueue-next-task params) => params
      (provided
       (successful? ..result..) => true
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
