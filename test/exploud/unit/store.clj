(ns exploud.unit.store
  (:require [exploud.store :refer :all]
            [midje.sweet :refer :all]
            [monger
             [collection :as mc]
             [operators :refer :all]]))

(fact-group

            (fact "Swapping '_id' with 'id' works"
                  (swap-mongo-id {:_id "identifier"}) => {:id "identifier"})

            (fact "Swapping 'id' with '_id' works"
                  (swap-id {:id "identifier"}) => {:_id "identifier"})

            (fact "We can get deployments"
                  (get-deployment ..deploy-id..) => {:id ..deploy-id..}
                  (provided
                   (mc/find-map-by-id "deployments" ..deploy-id..) => {:_id ..deploy-id..}))

            (fact "We can store deployments"
                  (store-deployment {:id ..deploy-id..
                                     :something "whatever"}) => nil
                  (provided
                   (mc/upsert "deployments"
                              {:_id ..deploy-id..}
                              {:_id ..deploy-id..
                               :something "whatever"}) => ..result..))

            (fact "Updating a task in a deployment works"
                  (update-task-in-deployment {:tasks
                                              [{:id ..id-1.. :log []}
                                               {:id ..id-2.. :log []}
                                               {:id ..id-3.. :log []}]}
                                             {:id ..id-2.. :log ["hello" "world"]}) =>
                                             {:tasks [{:id ..id-1.. :log []}
                                                      {:id ..id-2.. :log ["hello" "world"]}
                                                      {:id ..id-3.. :log []}]})

            (fact "We can store tasks"
                  (store-task ..deploy-id.. ..task..) => nil
                  (provided
                   (get-deployment ..deploy-id..) => ..deploy..
                   (update-task-in-deployment ..deploy.. ..task..) => ..amended-deploy..
                   (store-deployment ..amended-deploy..) => ..store-result..))

            (fact "We can find deployments with incomplete tasks"
                  (deployments-with-incomplete-tasks) => [{:id ..deploy-1..
                                                           :tasks [..task-1..]}
                                                          {:id ..deploy-2..
                                                           :tasks [..task-2..]}]
                  (provided
                   (mc/find-maps "deployments"
                                  {:tasks {$elemMatch {$nor [{:status "completed"} {:status "failed"} {:status "terminated"}]}}}
                                  ["tasks.$"]) => [{:id ..deploy-1..
                                                    :tasks [..task-1..]}
                                                   {:id ..deploy-2..
                                                    :tasks [..task-2..]}])))
