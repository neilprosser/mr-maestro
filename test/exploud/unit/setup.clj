(ns exploud.unit.setup
  (:require [exploud
             [asgard_new :as asgard]
             [deployment :as deployment]
             [setup :refer :all]
             [store :as store]]
            [midje.sweet :refer :all]))

(fact-group

 (fact "that we pick up incomplete tasks and do the right thing"
       (pick-up-tasks)
       => nil
       (provided
        (store/deployments-with-incomplete-tasks)
        => [{:id ..deploy-1..
             :tasks [..task-1..]}
            {:id ..deploy-2..
             :tasks [..task-2..]}]
        (asgard/track-until-completed
         ..deploy-1..
         ..task-1..
         3600
         deployment/task-finished
         deployment/task-timed-out)
        => nil
        (asgard/track-until-completed
         ..deploy-2..
         ..task-2..
         3600
         deployment/task-finished
         deployment/task-timed-out)
        => nil))

 (fact "that if we pick up incomplete tasks and there's nothing we handle it gracefully"
       (pick-up-tasks)
       => nil
       (provided
        (store/deployments-with-incomplete-tasks)
        => nil)))
