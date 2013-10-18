(ns exploud.unit.deployment
  (:use [exploud.deployment :refer :all]
        [midje.sweet :refer :all]))

(fact-group

 (fact "the standard deployment tasks for an application are all there and in the correct order"
       (map :action (create-standard-deployment-tasks))
       => [:create-next-asg
           :wait-for-health
           :enable-asg
           :disable-asg
           :delete-asg])

 (fact "the standard deployment tasks all have a status of `pending`"
       (->> (create-standard-deployment-tasks)
            (map :status)
            (filter (fn [t] (= (:action t) "pending")))
            empty?)))
