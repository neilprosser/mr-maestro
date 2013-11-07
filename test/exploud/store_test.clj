(ns exploud.store_test
  (:refer-clojure :exclude [sort find])
  (:require [exploud.store :refer :all]
            [midje.sweet :refer :all]
            [monger
             [collection :as mc]
             [operators :refer :all]
             [query :as mq]])
  (:import clojure.lang.ExceptionInfo))

(fact "that swapping '_id' with 'id' works"
      (swap-mongo-id {:_id "identifier"}) => {:id "identifier"})

(fact "that swapping 'id' with '_id' works"
      (swap-id {:id "identifier"}) => {:_id "identifier"})

(fact "that we can get deployments using the defaut settings"
      (get-deployments {})
      => [{:id "id"}]
      (provided
       (deployments-collection)
       => ..collection..
       (mq/exec (contains {:skip 0
                           :limit 10
                           :query {}
                           :sort {:start -1}}))
       => [{:_id "id"}]))

(fact "that we can get deployments and limit the number that come back"
      (get-deployments {:size 1})
      => [{:id "id"}]
      (provided
       (deployments-collection)
       => ..collection..
       (mq/exec (contains {:limit 1}))
       => [{:_id "id"}]))

(fact "that we can get deployments and skip some results"
      (get-deployments {:from 10})
      => [{:id "id"}]
      (provided
       (deployments-collection)
       => ..collection..
       (mq/exec (contains {:skip 10}))
       => [{:_id "id"}]))

(fact "that we can get deployments by application"
      (get-deployments {:application "application"})
      => [{:id "id"}]
      (provided
       (deployments-collection)
       => ..collection..
       (mq/exec (contains {:query {:application "application"}}))
       => [{:_id "id"}]))

(fact "that we can get deployments after a particular date"
      (get-deployments {:start-from ..start..})
      => [{:id "id"}]
      (provided
       (deployments-collection)
       => ..collection..
       (mq/exec (contains {:query {:start {$gte ..start..}}}))
       => [{:_id "id"}]))

(fact "that we can get deployments before a particular date"
      (get-deployments {:start-to ..end..})
      => [{:id "id"}]
      (provided
       (deployments-collection)
       => ..collection..
       (mq/exec (contains {:query {:start {$lt ..end..}}}))
       => [{:_id "id"}]))

(fact "that we can get deployments started between two dates"
      (get-deployments {:start-from ..start.. :start-to ..end..})
      => [{:id "id"}]
      (provided
       (deployments-collection)
       => ..collection..
       (mq/exec (contains {:query {:start {$gte ..start.. $lt ..end..}}}))
       => [{:_id "id"}]))

(fact "that we can get deployments by ID"
      (get-deployment ..deploy-id..) => {:id ..deploy-id..}
      (provided
       (mc/find-map-by-id "deployments" ..deploy-id..) => {:_id ..deploy-id..}))

(fact "that we can store deployments"
      (store-deployment {:id ..deploy-id..
                         :something "whatever"}) => nil
                         (provided
                          (mc/upsert "deployments"
                                     {:_id ..deploy-id..}
                                     {:_id ..deploy-id..
                                      :something "whatever"}) => ..result..))

(fact "that updating a task in a deployment works"
      (update-task-in-deployment {:tasks
                                  [{:id ..id-1.. :log []}
                                   {:id ..id-2.. :log []}
                                   {:id ..id-3.. :log []}]}
                                 {:id ..id-2.. :log ["hello" "world"]}) =>
                                 {:tasks [{:id ..id-1.. :log []}
                                          {:id ..id-2.. :log ["hello" "world"]}
                                          {:id ..id-3.. :log []}]})

(fact "that we can store tasks"
      (store-task ..deploy-id.. ..task..) => nil
      (provided
       (get-deployment ..deploy-id..) => ..deploy..
       (update-task-in-deployment ..deploy.. ..task..) => ..amended-deploy..
       (store-deployment ..amended-deploy..) => ..store-result..))

(fact "that we can find deployments with incomplete tasks"
      (deployments-with-incomplete-tasks) => [{:id ..deploy-1..
                                               :tasks [..task-1..]}
                                              {:id ..deploy-2..
                                               :tasks [..task-2..]}]
      (provided
       (mc/find-maps "deployments"
                     {:tasks {$elemMatch {$nor [{:status "completed"} {:status "failed"} {:status "terminated"} {:status "pending"} {:status "skipped"}]}}}
                     ["tasks.$"]) => [{:id ..deploy-1..
                                       :tasks [..task-1..]}
                                      {:id ..deploy-2..
                                       :tasks [..task-2..]}]))

(fact "that we can add to existing deployment parameters"
      (add-to-deployment-parameters ..deploy-id.. {:p1 "p1"
                                                   :p2 "p2"})
      => nil
      (provided
       (get-deployment ..deploy-id..)
       => {:id ..deploy-id..
           :parameters {:p2 "old-p2"
                        :p3 "p3"}}
       (store-deployment {:id ..deploy-id..
                          :parameters {:p1 "p1"
                                       :p2 "p2"
                                       :p3 "p3"}})
       => ..store-result..))
