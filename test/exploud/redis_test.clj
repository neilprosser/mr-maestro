(ns exploud.redis-test
  (:require [exploud.redis :refer :all]
            [midje.sweet :refer :all]
            [taoensso.carmine :as car]
            [taoensso.carmine.message-queue :as car-mq])
  (:import clojure.lang.ExceptionInfo))

(binding [*dummy-connection* true]

  (fact "Enqueuing something does the right thing"
        (enqueue ..task..) => ..id..
        (provided
         (car-mq/enqueue "scheduled-tasks" ..task..) => ..id..))

  (fact "that determining whether Exploud is locked does what it's supposed to do"
        (locked?) => ..result..
        (provided
         (car/get "exploud:lock") => ..result..))

  (fact "that locking Exploud does what it's supposed to do"
        (lock) => ..result..
        (provided
         (car/set "exploud:lock" "true") => ..result..))

  (fact "that unlocking Exploud does what it's supposed to do"
        (unlock) => ..result..
        (provided
         (car/del "exploud:lock") => ..result..))

  (fact "Application/environment combination is not in-progress when not present"
        (in-progress? "app" "env" "region") => falsey
        (provided
         (car/hget "exploud:deployments:in-progress" "app-env-region") => nil))

  (fact "Application/environment combination is in-progress when present"
        (in-progress? "app" "env" "region") => truthy
        (provided
         (car/hget "exploud:deployments:in-progress" "app-env-region") => "id"))

  (fact "that retrieving in-progress deployments works"
        (in-progress) => [{:application "app" :environment "env" :id "id" :region "reg"}
                          {:application "ppa" :environment "vne" :id "di" :region "ger"}]
        (provided
         (car/hgetall "exploud:deployments:in-progress") => ["app-env-reg" "id" "ppa-vne-ger" "di"]))

  (def begin-deployment-params
    {:application "app"
     :environment "env"
     :id "id"
     :region "region"})

  (fact "Beginning a deployment when one doesn't exist gives true"
        (begin-deployment begin-deployment-params) => true
        (provided
         (car/hsetnx "exploud:deployments:in-progress" "app-env-region" "id") => 1))

  (fact "Beginning a deployment when one already exists give false"
        (begin-deployment begin-deployment-params) => false
        (provided
         (car/hsetnx "exploud:deployments:in-progress" "app-env-region" "id") => 0))

  (def end-deployment-params
    {:application "app"
     :environment "env"
     :region "region"})

  (fact "Ending a deployment which exists returns true"
        (end-deployment end-deployment-params) => true
        (provided
         (car/hdel "exploud:deployments:in-progress" "app-env-region") => 1))

  (fact "Ending a deployment which didn't exist returns false"
        (end-deployment end-deployment-params) => false
        (provided
         (car/hdel "exploud:deployments:in-progress" "app-env-region") => 0))

  (fact "that obtaining the queue status works"
        (queue-status) => ..status..
        (provided
         (car-mq/queue-status anything "scheduled-tasks") => ..status..)))
