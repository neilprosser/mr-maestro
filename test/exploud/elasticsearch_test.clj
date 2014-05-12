(ns exploud.elasticsearch-test
  (:require [clojurewerkz.elastisch.query :as q]
            [clojurewerkz.elastisch.rest.document :as esd]
            [exploud.elasticsearch :refer :all]
            [midje.sweet :refer :all]))

(fact "that our start date facet is correct"
      (start-date-facet) => {:date_histogram {:field "start" :interval "month"}})

(fact "that our application facet is correct"
      (application-facet) => {:terms {:field "application"
                                      :order "term"
                                      :size 1000}})

(fact "that our user facet is correct"
      (user-facet) => {:terms {:field "user" :order "term" :size 1000}})

(fact "that our completed status filter is correct"
      (completed-status-filter) => {:term {:status "completed"}})

(fact "that our environment filter is correct"
      (environment-filter ..env..) => {:term {:environment ..env..}})

(fact "that we give back nil if there is no deployment for the given empty results"
      (nil-if-no-deployment "id" [])
      => nil
      (provided
       (deployment "id") => nil))

(fact "that we give back the results if they aren't empty and make sure we don't check for a deployment"
      (nil-if-no-deployment "id" ["something"])
      => ["something"]
      (provided
       (deployment "id") => {} :times 0))

(fact "that we give back the result if they are empty but there is a deployment"
      (nil-if-no-deployment "id" [])
      => []
      (provided
       (deployment "id") => {}))

(fact "that getting the deployments by user maps results correctly"
      (deployments-by-user) => [{:user "user1"
                                 :count 1}
                                {:user "user2"
                                 :count 2}]
      (provided
       (completed-status-filter) => ..completed-status-filter..
       (user-facet) => ..user-facet..
       (q/filtered :query (q/match-all) :filter ..completed-status-filter..) => ..query..
       (esd/search anything "exploud" "deployment" :query ..query.. :size 0 :facets {:user ..user-facet..}) => {:facets {:user {:terms [{:term "user1"
                                                                                                                                                                    :count 1}
                                                                                                                                                                   {:term "user2"
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
       (esd/search anything "exploud" "deployment" :query ..query.. :size 0 :facets {:application ..application-facet..}) => {:facets {:application {:terms [{:term "app1"
                                                                                                                                                     :count 1}
                                                                                                                                                    {:term "app2"
                                                                                                                                                     :count 2}]}}}))

(fact "that getting the deployments by month maps results correctly"
      (deployments-by-month) => [{:date "2014-03-01T00:00:00.000Z"
                                  :count 12}
                                 {:date "2014-04-01T00:00:00.000Z"
                                  :count 2}]
      (provided
       (start-date-facet) => ..start-date-facet..
       (esd/search anything "exploud" "deployment" :query anything :size 0 :facets {:date ..start-date-facet..}) => {:facets {:date {:entries [{:time 1393632000000
                                                                                                                                       :count 12}
                                                                                                                                      {:time 1396310400000
                                                                                                                                       :count 2}]}}}))

(fact "that getting the deployments in an environment by month maps results correctly"
      (deployments-in-environment-by-month "poke") => [{:date "2014-03-01T00:00:00.000Z"
                                                        :count 12}
                                                       {:date "2014-04-01T00:00:00.000Z"
                                                        :count 2}]
      (provided
       (start-date-facet) => ..start-date-facet..
       (completed-status-filter) => ..completed-status-filter..
       (environment-filter "poke") => ..environment-filter..
       (q/filtered :query (q/match-all) :filter {:and {:filters [..completed-status-filter.. ..environment-filter..]}}) => ..query..
       (esd/search anything "exploud" "deployment" :query ..query.. :size 0 :facets {:date ..start-date-facet..}) => {:facets {:date {:entries [{:time 1393632000000
                                                                                                                                        :count 12}
                                                                                                                                       {:time 1396310400000
                                                                                                                                        :count 2}]}}}))
