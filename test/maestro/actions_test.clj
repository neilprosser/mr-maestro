(ns maestro.actions-test
  (:require [maestro.actions :refer :all]
            [midje.sweet :refer :all]))

(fact "that getting the next action for a legacy action works"
      (action-after :exploud.messages.data/create-names)
      => :maestro.messages.data/get-image-details)

(fact "that getting the next action works"
      (action-after :maestro.messages.data/create-names)
      => :maestro.messages.data/get-image-details)

(fact "that getting the sequence number of a legacy action works"
      (sequence-number :exploud.messages.health/wait-for-load-balancers-to-be-healthy) => 47)

(fact "that getting the sequence number works"
      (sequence-number :maestro.messages.health/wait-for-load-balancers-to-be-healthy) => 47)

(fact "that getting the resume action for a running task gives the action of that task"
      (resume-action [{} {:action "action" :status "running"}]) => :action)

(fact "that getting the resume action for a complete task gives the next action"
      (resume-action [{:action "action" :status "completed"}]) => :next-action
      (provided
       (action-after :action) => :next-action))
