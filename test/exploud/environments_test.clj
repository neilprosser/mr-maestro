(ns exploud.environments-test
  (:require [exploud
             [environments :refer :all]
             [onix :as onix]]
            [midje.sweet :refer :all]
            [overtone.at-at :as at]))

(fact "that getting nil back when updating environments does not replace the value"
      (do (reset! environments-atom nil) (update-environments) (environments)) => nil
      (provided
       (onix/environments) => nil))

(fact "that an exception while getting environments does not replace the value"
      (do (reset! environments-atom nil) (update-environments) (environments)) => nil
      (provided
       (onix/environments) =throws=> (ex-info "Busted" {})))

(fact "that updating our environments does what we expect"
      (do (reset! environments-atom nil) (update-environments) (environments)) => {:env1 {:name "env1"
                                                                                          :metadata {:first "metadata"}}
                                                                                   :env2 {:name "env2"
                                                                                          :metadata {:second "metadata"}}}
      (provided
       (onix/environments) => ["env1" "env2"]
       (onix/environment "env1") => {:name "env1" :metadata {:first "metadata"}}
       (onix/environment "env2") => {:name "env2" :metadata {:second "metadata"}}))

(fact "that getting a single environment does what we expect when the environment exists"
      (environment "env2") => ..env2..
      (provided
       (environments) => {:env1 ..env1..
                          :env2 ..env2..}))

(fact "that we get nil back when an environment doesn't exist"
      (environment "unknown") => nil
      (provided
       (environments) => {:env1 ..env1..
                          :env2 ..env2..}))
