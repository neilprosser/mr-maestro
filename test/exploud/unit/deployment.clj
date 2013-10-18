(ns exploud.unit.deployment
  (:use [exploud
         [asgard_new :as asgard]
         [deployment :refer :all]]
        [midje.sweet :refer :all]))

(fact-group

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

 (fact "that the first task of a deployment for an application with an ASG has the `old-asg` parameter"
       (first (prepare-deployment-tasks "region" "application" "environment"))
       => (contains {:params {:old-asg ..old-asg..}})
       (provided
        (asgard/last-auto-scaling-group "region" "application-environment")
        => {:autoScalingGroupName ..old-asg..}))

 (fact "that the first task of a deployment for an application without an ASG has the no `old-asg` parameter"
       (first (prepare-deployment-tasks "region" "application" "environment"))
       =not=> (contains {:params {}})
       (provided
        (asgard/last-auto-scaling-group "region" "application-environment")
        => nil)))
