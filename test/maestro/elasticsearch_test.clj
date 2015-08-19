(ns maestro.elasticsearch-test
  (:require [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest.document :as esd]
            [clojurewerkz.elastisch.rest.index :as esi]
            [maestro.elasticsearch :refer :all]
            [midje.sweet :refer :all]))

(fact "that our start date facet is correct"
      (start-date-facet ..interval..) => {:date_histogram {:field "start" :interval ..interval..}})

(fact "that our application facet is correct"
      (application-facet) => {:terms {:field "application"
                                      :order "term"
                                      :size 1000}})

(fact "that our user facet is correct"
      (user-facet) => {:terms {:field "user" :order "term" :size 1000}})

(fact "that our completed status filter is correct"
      (completed-status-filter) => {:term {:status "completed"}})

(fact "that our failed status filter is correct"
      (failed-status-filter) => {:or [{:term {:status "failed"}} {:and [{:term {:status "completed"}} {:term {:undo true}}]}]})

(fact "that our environment filter is correct"
      (environment-filter ..env..) => {:term {:environment ..env..}})

(fact "that our user filter is correct"
      (user-filter ..user..) => {:term {:user ..user..}})

(fact "that our parent filter is correct"
      (parent-filter ..document-type.. ..parent-id..) => {:has_parent {:type ..document-type..
                                                                       :query {:term {:_id {:value ..parent-id..}}}}})

(fact "that upserting a deployment calls through to Elasticsearch and removes the ID from the document"
      (upsert-deployment "id" {:id "id-field" :some "field"})
      => ..put-result..
      (provided
       (esd/put anything "maestro" "deployment" "id" {:some "field"} :refresh true) => ..put-result..))

(fact "that updating a deployment calls through to Elasticsearch and removes the ID from the partial document"
      (update-deployment "id" {:id "id-field" :some "field"})
      => ..update-result..
      (provided
       (esd/update-with-partial-doc anything "maestro" "deployment" "id" {:some "field"} :refresh true) => ..update-result..))

(fact "that attempting to get a deployment which doesn't exist gives nil"
      (deployment "id")
      => nil
      (provided
       (esd/get anything "maestro" "deployment" "id") => {:_source nil}))

(fact "that getting a deployment adds the ID to the `_source`"
      (deployment "id")
      => {:id "id"
          :some "field"}
      (provided
       (esd/get anything "maestro" "deployment" "id") => {:_source {:some "field"}}))

(fact "that deleting a deployment also deletes associated documents and refreshes the index"
      (delete-deployment "id")
      => ..refresh-result..
      (provided
       (esd/delete anything "maestro" "deployment" "id") => ..delete-result..
       (esd/delete-by-query-across-all-types anything "maestro" {:filtered {:query {:match_all {}}
                                                                            :filter (parent-filter "deployment" "id")}}) => ..delete-all-types-result..
       (esi/refresh anything "maestro") => ..refresh-result..))

(fact "that getting deployments with no filters works"
      (get-deployments {})
      => [{:id "id" :some "field"}]
      (provided
       (esd/search anything "maestro" "deployment" :filter {:and {:filters []}} :size 10 :from 0 :sort {:start "desc"} :_source {:include ["application" "end" "environment" "message" "phase" "region" "start" "status" "user" "new-state.hash" "new-state.image-details" "previous-state.hash" "previous-state.image-details"]}) => {:hits {:hits [{:_id "id" :_source {:some "field"}}]}}))

(fact "that using an application filter when getting deployments works"
      (get-deployments {:application "application"})
      => [{:id "id" :some "field"}]
      (provided
       (esd/search anything "maestro" "deployment" :filter {:and {:filters [{:term {:application "application"}}]}} :size 10 :from 0 :sort {:start "desc"} :_source {:include ["application" "end" "environment" "message" "phase" "region" "start" "status" "user" "new-state.hash" "new-state.image-details" "previous-state.hash" "previous-state.image-details"]}) => {:hits {:hits [{:_id "id" :_source {:some "field"}}]}}))

(fact "that using an environment filter when getting deployments works"
      (get-deployments {:environment "environment"})
      => [{:id "id" :some "field"}]
      (provided
       (esd/search anything "maestro" "deployment" :filter {:and {:filters [{:term {:environment "environment"}}]}} :size 10 :from 0 :sort {:start "desc"} :_source {:include ["application" "end" "environment" "message" "phase" "region" "start" "status" "user" "new-state.hash" "new-state.image-details" "previous-state.hash" "previous-state.image-details"]}) => {:hits {:hits [{:_id "id" :_source {:some "field"}}]}}))

(fact "that specifying that we want to get full deployments back when getting deployments works"
      (get-deployments {:full? true})
      => [{:id "id" :some "field"}]
      (provided
       (esd/search anything "maestro" "deployment" :filter {:and {:filters []}} :size 10 :from 0 :sort {:start "desc"} :_source true) => {:hits {:hits [{:_id "id" :_source {:some "field"}}]}}))

(fact "that using a region filter when getting deployments works"
      (get-deployments {:region "region"})
      => [{:id "id" :some "field"}]
      (provided
       (esd/search anything "maestro" "deployment" :filter {:and {:filters [{:term {:region "region"}}]}} :size 10 :from 0 :sort {:start "desc"} :_source {:include ["application" "end" "environment" "message" "phase" "region" "start" "status" "user" "new-state.hash" "new-state.image-details" "previous-state.hash" "previous-state.image-details"]}) => {:hits {:hits [{:_id "id" :_source {:some "field"}}]}}))

(fact "that using a status filter when getting deployments works"
      (get-deployments {:status "status"})
      => [{:id "id" :some "field"}]
      (provided
       (esd/search anything "maestro" "deployment" :filter {:and {:filters [{:term {:status "status"}}]}} :size 10 :from 0 :sort {:start "desc"} :_source {:include ["application" "end" "environment" "message" "phase" "region" "start" "status" "user" "new-state.hash" "new-state.image-details" "previous-state.hash" "previous-state.image-details"]}) => {:hits {:hits [{:_id "id" :_source {:some "field"}}]}}))

(fact "that filtering deployments after their start date works"
      (get-deployments {:start-from "start-from"})
      => [{:id "id" :some "field"}]
      (provided
       (esd/search anything "maestro" "deployment" :filter {:and {:filters [{:range {:start {:gte "start-from"}}}]}} :size 10 :from 0 :sort {:start "desc"} :_source {:include ["application" "end" "environment" "message" "phase" "region" "start" "status" "user" "new-state.hash" "new-state.image-details" "previous-state.hash" "previous-state.image-details"]}) => {:hits {:hits [{:_id "id" :_source {:some "field"}}]}}))

(fact "that filtering deployments before their start date works"
      (get-deployments {:start-to "start-to"})
      => [{:id "id" :some "field"}]
      (provided
       (esd/search anything "maestro" "deployment" :filter {:and {:filters [{:range {:start {:lt "start-to"}}}]}} :size 10 :from 0 :sort {:start "desc"} :_source {:include ["application" "end" "environment" "message" "phase" "region" "start" "status" "user" "new-state.hash" "new-state.image-details" "previous-state.hash" "previous-state.image-details"]}) => {:hits {:hits [{:_id "id" :_source {:some "field"}}]}}))

(fact "that filtering deployments by their start date within a range works"
      (get-deployments {:start-from "start-from" :start-to "start-to"})
      => [{:id "id" :some "field"}]
      (provided
       (esd/search anything "maestro" "deployment" :filter {:and {:filters [{:range {:start {:gte "start-from" :lt "start-to"}}}]}} :size 10 :from 0 :sort {:start "desc"} :_source {:include ["application" "end" "environment" "message" "phase" "region" "start" "status" "user" "new-state.hash" "new-state.image-details" "previous-state.hash" "previous-state.image-details"]}) => {:hits {:hits [{:_id "id" :_source {:some "field"}}]}}))

(fact "that using an offset when getting deployments works"
      (get-deployments {:from 5})
      => [{:id "id" :some "field"}]
      (provided
       (esd/search anything "maestro" "deployment" :filter {:and {:filters []}} :size 10 :from 5 :sort {:start "desc"} :_source {:include ["application" "end" "environment" "message" "phase" "region" "start" "status" "user" "new-state.hash" "new-state.image-details" "previous-state.hash" "previous-state.image-details"]}) => {:hits {:hits [{:_id "id" :_source {:some "field"}}]}}))

(fact "that choosing the number of deployments to return when getting deployments works"
      (get-deployments {:size 1})
      => [{:id "id" :some "field"}]
      (provided
       (esd/search anything "maestro" "deployment" :filter {:and {:filters []}} :size 1 :from 0 :sort {:start "desc"} :_source {:include ["application" "end" "environment" "message" "phase" "region" "start" "status" "user" "new-state.hash" "new-state.image-details" "previous-state.hash" "previous-state.image-details"]}) => {:hits {:hits [{:_id "id" :_source {:some "field"}}]}}))

(fact "that combining a few filters when getting deployments works"
      (get-deployments {:application "application" :start-from "start-from" :status "status"})
      => [{:id "id" :some "field"}]
      (provided
       (esd/search anything "maestro" "deployment" :filter {:and {:filters [{:term {:application "application"}} {:term {:status "status"}} {:range {:start {:gte "start-from"}}}]}} :size 10 :from 0 :sort {:start "desc"} :_source {:include ["application" "end" "environment" "message" "phase" "region" "start" "status" "user" "new-state.hash" "new-state.image-details" "previous-state.hash" "previous-state.image-details"]}) => {:hits {:hits [{:_id "id" :_source {:some "field"}}]}}))

(fact "that creating a task removes the ID from the document"
      (create-task "task-id" "deployment-id" {:id "document-id" :some "field"})
      => ..put-result..
      (provided
       (esd/put anything "maestro" "task" "task-id" {:some "field"} :parent "deployment-id" :refresh true) => ..put-result..))

(fact "that updating a task removes the ID from the document"
      (update-task "task-id" "deployment-id" {:id "document-id" :some "field"})
      => ..update-result..
      (provided
       (esd/update-with-partial-doc anything "maestro" "task" "task-id" {:some "field"} :parent "deployment-id" :refresh true) => ..update-result..))

(fact "that writing a log removes the ID from the document"
      (write-log "log-id" "deployment-id" {:id "document-id" :some "field"})
      => ..put-result..
      (provided
       (esd/put anything "maestro" "log" "log-id" {:some "field"} :parent "deployment-id" :refresh true) => ..put-result..))

(fact "that getting deployment tasks returns nothing if the deployment doesn't exist"
      (deployment-tasks "deployment-id")
      => nil
      (provided
       (esd/search anything "maestro" "task" :query {:match_all {}} :filter (parent-filter "deployment" "deployment-id") :sort {:sequence "asc"} :size 10000) => {:hits {:hits []}}
       (deployment "deployment-id") => nil))

(fact "that getting deployment tasks returns an empty list when there are no tasks but there is a deployment"
      (deployment-tasks "deployment-id")
      => []
      (provided
       (esd/search anything "maestro" "task" :query {:match_all {}} :filter (parent-filter "deployment" "deployment-id") :sort {:sequence "asc"} :size 10000) => {:hits {:hits []}}
       (deployment "deployment-id") => {}))

(fact "that getting deployment tasks includes the ID and removes the sequence from the hit when there are hits and doesn't ask for the deployment"
      (deployment-tasks "deployment-id")
      => [{:id "task-id" :some "field"}]
      (provided
       (esd/search anything "maestro" "task" :query {:match_all {}} :filter (parent-filter "deployment" "deployment-id") :sort {:sequence "asc"} :size 10000) => {:hits {:hits [{:_id "task-id" :_source {:sequence 1 :some "field"}}]}}
       (deployment anything) => nil :times 0))

(fact "that getting deployment logs returns nothing if the deployment doesn't exist"
      (deployment-logs "deployment-id" nil)
      => nil
      (provided
       (esd/search anything "maestro" "log" :query {:match_all {}} :filter {:and {:filters [(parent-filter "deployment" "deployment-id")]}} :sort {:date "asc"} :size 10000) => {:hits {:hits []}}
       (deployment "deployment-id") => nil))

(fact "that getting deployment logs returns an empty list when there are no logs but there is a deployment"
      (deployment-logs "deployment-id" nil)
      => []
      (provided
       (esd/search anything "maestro" "log" :query {:match_all {}} :filter {:and {:filters [(parent-filter "deployment" "deployment-id")]}} :sort {:date "asc"} :size 10000) => {:hits {:hits []}}
       (deployment "deployment-id") => {}))

(fact "that getting deployment logs includes the ID from the hit when there are hits and doesn't ask for the deployment"
      (deployment-logs "deployment-id" nil)
      => [{:id "task-id" :some "field"}]
      (provided
       (esd/search anything "maestro" "log" :query {:match_all {}} :filter {:and {:filters [(parent-filter "deployment" "deployment-id")]}} :sort {:date "asc"} :size 10000) => {:hits {:hits [{:_id "task-id" :_source {:some "field"}}]}}
       (deployment anything) => nil :times 0))

(fact "that getting deployment logs and using a date filter works"
      (deployment-logs "deployment-id" ..since..)
      => [{:id "task-id" :some "field"}]
      (provided
       (esd/search anything "maestro" "log" :query {:match_all {}} :filter {:and {:filters [(parent-filter "deployment" "deployment-id") {:range {:date {:gt ..since..}}}]}} :sort {:date "asc"} :size 10000) => {:hits {:hits [{:_id "task-id" :_source {:some "field"}}]}}
       (deployment anything) => nil :times 0))

(fact "that getting the deployments by user maps results correctly"
      (deployments-by-user) => [{:user "user1"
                                 :count 1}
                                {:user "user2"
                                 :count 2}]
      (provided
       (esd/search anything "maestro" "deployment" :query (q/filtered :query (q/match-all) :filter (completed-status-filter)) :size 0 :facets {:user (user-facet)}) => {:facets {:user {:terms [{:term "user1"
                                                                                                                                                                                                 :count 1}
                                                                                                                                                                                                {:term "user2"
                                                                                                                                                                                                 :count 2}]}}}))

(fact "that getting the failed deployments by user maps results correctly"
      (failed-deployments-by-user) => [{:user "user1"
                                        :count 1}
                                       {:user "user2"
                                        :count 2}]
      (provided
       (esd/search anything "maestro" "deployment" :query (q/filtered :query (q/match-all) :filter (failed-status-filter)) :size 0 :facets {:user (user-facet)}) => {:facets {:user {:terms [{:term "user1"
                                                                                                                                                                                              :count 1}
                                                                                                                                                                                             {:term "user2"
                                                                                                                                                                                              :count 2}]}}}))

(fact "that getting deployments by user and by application maps results correctly"
      (deployments-by-user-by-application "user")
      => [{:application "app1"
           :count 1}
          {:application "app2"
           :count 2}]
      (provided
       (esd/search anything "maestro" "deployment" :query (q/filtered :query (q/match-all) :filter {:and {:filters [(completed-status-filter) (user-filter "user")]}}) :size 0 :facets {:application (application-facet)}) => {:facets {:application {:terms [{:term "app1"
                                                                                                                                                                                                                                                               :count 1}
                                                                                                                                                                                                                                                              {:term "app2"
                                                                                                                                                                                                                                                               :count 2}]}}}))

(fact "that getting the deployments by application maps results correctly"
      (deployments-by-application) => [{:application "app1"
                                        :count 1}
                                       {:application "app2"
                                        :count 2}]
      (provided
       (completed-status-filter) => ..completed-status-filter..
       (application-facet) => ..application-facet..
       (q/filtered :query (q/match-all) :filter ..completed-status-filter..) => ..query..
       (esd/search anything "maestro" "deployment" :query ..query.. :size 0 :facets {:application ..application-facet..}) => {:facets {:application {:terms [{:term "app1"
                                                                                                                                                              :count 1}
                                                                                                                                                             {:term "app2"
                                                                                                                                                              :count 2}]}}}))

(fact "that getting the deployments by year maps results correctly"
      (deployments-by-year) => [{:date "2014-01-01T00:00:00.000Z"
                                 :count 12}
                                {:date "2015-01-01T00:00:00.000Z"
                                 :count 2}]
      (provided
       (esd/search anything "maestro" "deployment" :query anything :size 0 :facets {:date (start-date-facet "year")}) => {:facets {:date {:entries [{:time 1388534400000
                                                                                                                                                     :count 12}
                                                                                                                                                    {:time 1420070400000
                                                                                                                                                     :count 2}]}}}))

(fact "that getting the deployments by month maps results correctly"
      (deployments-by-month) => [{:date "2014-03-01T00:00:00.000Z"
                                  :count 12}
                                 {:date "2014-04-01T00:00:00.000Z"
                                  :count 2}]
      (provided
       (esd/search anything "maestro" "deployment" :query anything :size 0 :facets {:date (start-date-facet "month")}) => {:facets {:date {:entries [{:time 1393632000000
                                                                                                                                                      :count 12}
                                                                                                                                                     {:time 1396310400000
                                                                                                                                                      :count 2}]}}}))

(fact "that getting the deployments by day maps results correctly"
      (deployments-by-day) => [{:date "2014-03-01T00:00:00.000Z"
                                :count 12}
                               {:date "2014-03-02T00:00:00.000Z"
                                :count 2}]
      (provided
       (esd/search anything "maestro" "deployment" :query anything :size 0 :facets {:date (start-date-facet "day")}) => {:facets {:date {:entries [{:time 1393632000000
                                                                                                                                                    :count 12}
                                                                                                                                                   {:time 1393718400000
                                                                                                                                                    :count 2}]}}}))

(fact "that getting the deployments in an environment by year maps results correctly"
      (deployments-in-environment-by-year "poke") => [{:date "2014-01-01T00:00:00.000Z"
                                                       :count 12}
                                                      {:date "2015-01-01T00:00:00.000Z"
                                                       :count 2}]
      (provided
       (esd/search anything "maestro" "deployment" :query (q/filtered :query (q/match-all) :filter {:and {:filters [(completed-status-filter) (environment-filter "poke")]}}) :size 0 :facets {:date (start-date-facet "year")}) => {:facets {:date {:entries [{:time 1388534400000
                                                                                                                                                                                                                                                                 :count 12}
                                                                                                                                                                                                                                                                {:time 1420070400000
                                                                                                                                                                                                                                                                 :count 2}]}}}))

(fact "that getting the deployments in an environment by month maps results correctly"
      (deployments-in-environment-by-month "poke") => [{:date "2014-03-01T00:00:00.000Z"
                                                        :count 12}
                                                       {:date "2014-04-01T00:00:00.000Z"
                                                        :count 2}]
      (provided
       (esd/search anything "maestro" "deployment" :query (q/filtered :query (q/match-all) :filter {:and {:filters [(completed-status-filter) (environment-filter "poke")]}}) :size 0 :facets {:date (start-date-facet "month")}) => {:facets {:date {:entries [{:time 1393632000000
                                                                                                                                                                                                                                                                 :count 12}
                                                                                                                                                                                                                                                                {:time 1396310400000
                                                                                                                                                                                                                                                                 :count 2}]}}}))

(fact "that getting the deployments in an environment by day maps results correctly"
      (deployments-in-environment-by-day "poke") => [{:date "2014-03-01T00:00:00.000Z"
                                                      :count 12}
                                                     {:date "2014-03-02T00:00:00.000Z"
                                                      :count 2}]
      (provided
       (esd/search anything "maestro" "deployment" :query (q/filtered :query (q/match-all) :filter {:and {:filters [(completed-status-filter) (environment-filter "poke")]}}) :size 0 :facets {:date (start-date-facet "day")}) => {:facets {:date {:entries [{:time 1393632000000
                                                                                                                                                                                                                                                               :count 12}
                                                                                                                                                                                                                                                              {:time 1393718400000
                                                                                                                                                                                                                                                               :count 2}]}}}))

(fact "that we are healthy if we don't get an exception while checking health"
      (healthy?) => truthy
      (provided
       (deployment "healthcheck") => nil))

(fact "that we aren't healthy if we get an exception while checking health"
      (healthy?) => falsey
      (provided
       (deployment "healthcheck") =throws=> (ex-info "Busted" {})))
