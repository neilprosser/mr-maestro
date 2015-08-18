(ns maestro.environments-test
  (:require [maestro
             [environments :refer :all]
             [lister :as lister]]
            [midje.sweet :refer :all]
            [ninjakoala.ttlr :as ttlr]))

(fact "that we're healthy if there are some environments"
      (healthy?) => truthy
      (provided
       (environments) => {:env {}}))

(fact "that we aren't healthy if there aren't any environments"
      (healthy?) => falsey
      (provided
       (environments) => {}))

(fact "that updating our environments does what we expect"
      (update-environments) => {:env1 {:name "env1"
                                       :metadata {:first "metadata"}}
                                :env2 {:name "env2"
                                       :metadata {:second "metadata"}}}
      (provided
       (lister/environments) => ["env1" "env2"]
       (lister/environment "env1") => {:name "env1" :metadata {:first "metadata"}}
       (lister/environment "env2") => {:name "env2" :metadata {:second "metadata"}}))

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

(fact "that we get nil for an alert topic if the environment doesn't exist"
      (alert-topic "unknown") => nil
      (provided
       (environment "unknown") => nil))

(fact "that we get the correct alert topic back for an environment which does exist"
      (alert-topic "env") => "alert-topic"
      (provided
       (environment "env") => {:metadata {:alert-topic "alert-topic"}}))

(fact "that we get nil for the Healthy URL if the environment doesn't exist"
      (healthy-url "unknown") => nil
      (provided
       (environment "unknown") => nil))

(fact "that we get the correct Healthy URL for an environment which exists"
      (healthy-url "env") => "healthy-url"
      (provided
       (environment "env") => {:metadata {:healthy-url "healthy-url"}}))

(fact "that an environment should notify when specified"
      (should-notify? "env") => truthy
      (provided
       (environment "env") => {:metadata {:deployment-notifications true}}))

(fact "that an environment should not notify when specified"
      (should-notify? "env") => falsey
      (provided
       (environment "env") => {:metadata {:deployment-notifications false}}))

(fact "that an environment should not notify when nothing specified"
      (should-notify? "env") => falsey
      (provided
       (environment "env") => {:metadata {}}))
