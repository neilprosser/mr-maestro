(ns maestro.redis-test
  (:require [maestro.redis :refer :all]
            [midje.sweet :refer :all]
            [taoensso.carmine :as car]
            [taoensso.carmine.message-queue :as car-mq])
  (:import clojure.lang.ExceptionInfo))

(binding [*dummy-connection* true]

  (fact "that enqueuing something does the right thing"
        (enqueue ..task..) => ..id..
        (provided
         (car-mq/enqueue "scheduled-tasks" ..task..) => ..id..))

  (fact "that determining whether Maestro is locked does what it's supposed to do"
        (locked?) => ..result..
        (provided
         (car/get "maestro:lock") => ..result..))

  (fact "that locking Maestro does what it's supposed to do"
        (lock) => ..result..
        (provided
         (car/set "maestro:lock" "true") => ..result..))

  (fact "that unlocking Maestro does what it's supposed to do"
        (unlock) => ..result..
        (provided
         (car/del "maestro:lock") => ..result..))

  (fact "that determining whether something is not paused when not present"
        (paused? "app" "env" "region") => falsey
        (provided
         (car/hget "maestro:deployments:paused" "app-env-region") => nil))

  (fact "that determining whether something is paused when present"
        (paused? "app" "env" "region") => truthy
        (provided
         (car/hget "maestro:deployments:paused" "app-env-region") => "id"))

  (fact "that getting paused deployments works"
        (paused) => [{:application "app1" :environment "env1" :id "id1" :region "region1"}
                     {:application "app2" :environment "env2" :id "id2" :region "region2"}]
        (provided
         (car/hgetall "maestro:deployments:paused") => ["app1-env1-region1" "id1" "app2-env2-region2" "id2"]))

  (fact "that getting paused deployments works when nothing is paused"
        (paused) => []
        (provided
         (car/hgetall "maestro:deployments:paused") => []))

  (fact "that determining whether a pause is registered is truthy when present"
        (pause-registered? "app" "env" "region") => truthy
        (provided
         (car/sismember "maestro:deployments:awaiting-pause" "app-env-region") => 1))

  (fact "that determining whether a pause is registered is falsey when not present"
        (pause-registered? "app" "env" "region") => falsey
        (provided
         (car/sismember "maestro:deployments:awaiting-pause" "app-env-region") => 0))

  (fact "that getting all deployments awaiting a pause works"
        (awaiting-pause) => [{:application "app1" :environment "env1" :region "region1"}
                             {:application "app2" :environment "env2" :region "region2"}]
        (provided
         (car/smembers "maestro:deployments:awaiting-pause") => ["app1-env1-region1" "app2-env2-region2"]))

  (fact "that getting all deployments awaiting a pause works when there aren't any"
        (awaiting-pause) => []
        (provided
         (car/smembers "maestro:deployments:awaiting-pause") => []))

  (fact "that registering a pause is truthy when one wasn't already present"
        (register-pause "app" "env" "region") => truthy
        (provided
         (car/sadd "maestro:deployments:awaiting-pause" "app-env-region") => 1))

  (fact "that registering a pause is falsey when one was already present"
        (register-pause "app" "env" "region") => falsey
        (provided
         (car/sadd "maestro:deployments:awaiting-pause" "app-env-region") => 0))

  (fact "that unregistering a pause is truthy when one was present"
        (unregister-pause "app" "env" "region") => truthy
        (provided
         (car/srem "maestro:deployments:awaiting-pause" "app-env-region") => 1))

  (fact "that unregistering a pause is truthy when one was present"
        (unregister-pause "app" "env" "region") => falsey
        (provided
         (car/srem "maestro:deployments:awaiting-pause" "app-env-region") => 0))

  (fact "that determining whether a cancellation is registered is truthy when present"
        (cancel-registered? "app" "env" "region") => truthy
        (provided
         (car/sismember "maestro:deployments:awaiting-cancel" "app-env-region") => 1))

  (fact "that determining whether a cancellation is registered is falsey when not present"
        (cancel-registered? "app" "env" "region") => falsey
        (provided
         (car/sismember "maestro:deployments:awaiting-cancel" "app-env-region") => 0))

  (fact "that getting all deployments awaiting a cancellation works"
        (awaiting-cancel) => [{:application "app1" :environment "env1" :region "region1"}
                              {:application "app2" :environment "env2" :region "region2"}]
        (provided
         (car/smembers "maestro:deployments:awaiting-cancel") => ["app1-env1-region1" "app2-env2-region2"]))

  (fact "that getting all deployments awaiting a cancellation works when there aren't any"
        (awaiting-cancel) => []
        (provided
         (car/smembers "maestro:deployments:awaiting-cancel") => []))

  (fact "that registering a cancellation is truthy when one wasn't already present"
        (register-cancel "app" "env" "region") => truthy
        (provided
         (car/sadd "maestro:deployments:awaiting-cancel" "app-env-region") => 1))

  (fact "that registering a cancellation is falsey when one was already present"
        (register-cancel  "app" "env" "region") => falsey
        (provided
         (car/sadd "maestro:deployments:awaiting-cancel" "app-env-region") => 0))

  (fact "that unregistering a cancellation is truthy when one was present"
        (unregister-cancel "app" "env" "region") => truthy
        (provided
         (car/srem "maestro:deployments:awaiting-cancel" "app-env-region") => 1))

  (fact "that unregistering a cancellation is truthy when one was present"
        (unregister-cancel "app" "env" "region") => falsey
        (provided
         (car/srem "maestro:deployments:awaiting-cancel" "app-env-region") => 0))

  (fact "that a deployment is not in-progress when not present"
        (in-progress? "app" "env" "region") => falsey
        (provided
         (car/hget "maestro:deployments:in-progress" "app-env-region") => nil))

  (fact "that a deployment is in-progress when present"
        (in-progress? "app" "env" "region") => truthy
        (provided
         (car/hget "maestro:deployments:in-progress" "app-env-region") => "id"))

  (fact "that retrieving in-progress deployments works"
        (in-progress) => [{:application "app" :environment "env" :id "id" :region "reg"}
                          {:application "ppa" :environment "vne" :id "di" :region "ger"}]
        (provided
         (car/hgetall "maestro:deployments:in-progress") => ["app-env-reg" "id" "ppa-vne-ger" "di"]))

  (fact "that pausing a deployment is truthy when it wasn't already paused"
        (pause "app" "env" "id" "region") => truthy
        (provided
         (car/hsetnx "maestro:deployments:paused" "app-env-region" "id") => 1))

  (fact "that pausing a deployment is falsey when it wasn't already paused"
        (pause "app" "env" "id" "region") => falsey
        (provided
         (car/hsetnx "maestro:deployments:paused" "app-env-region" "id") => 0))

  (fact "that unpausing a deployment is truthy when it was already paused"
        (resume "app" "env" "region") => truthy
        (provided
         (car/srem "maestro:deployments:awaiting-cancel" "app-env-region") => 0
         (car/hdel "maestro:deployments:paused" "app-env-region") => 1))

  (fact "that unpausing a deployment is falsey when it wasn't already paused"
        (resume "app" "env" "region") => falsey
        (provided
         (car/srem "maestro:deployments:awaiting-cancel" "app-env-region") => 0
         (car/hdel "maestro:deployments:paused" "app-env-region") => 0))

  (def begin-deployment-params
    {:application "app"
     :environment "env"
     :id "id"
     :region "region"})

  (fact "that beginning a deployment when one doesn't exist gives true"
        (begin-deployment begin-deployment-params) => true
        (provided
         (car/hsetnx "maestro:deployments:in-progress" "app-env-region" "id") => 1))

  (fact "that beginning a deployment when one already exists give false"
        (begin-deployment begin-deployment-params) => false
        (provided
         (car/hsetnx "maestro:deployments:in-progress" "app-env-region" "id") => 0))

  (def end-deployment-params
    {:application "app"
     :environment "env"
     :region "region"})

  (fact "that ending a deployment which exists returns true"
        (end-deployment end-deployment-params) => true
        (provided
         (car/srem "maestro:deployments:awaiting-pause" "app-env-region") => 0
         (car/srem "maestro:deployments:awaiting-cancel" "app-env-region") => 0
         (car/hdel "maestro:deployments:in-progress" "app-env-region") => 1))

  (fact "that ending a deployment which didn't exist returns false"
        (end-deployment end-deployment-params) => false
        (provided
         (car/srem "maestro:deployments:awaiting-pause" "app-env-region") => 0
         (car/srem "maestro:deployments:awaiting-cancel" "app-env-region") => 0
         (car/hdel "maestro:deployments:in-progress" "app-env-region") => 0))

  (def cancel-deployment-params
    {:application "app"
     :environment "env"
     :region "region"})

  (fact "that cancelling a deployment which had a cancellation registered returns true"
        (cancel-deployment cancel-deployment-params) => true
        (provided
         (car/srem "maestro:deployments:awaiting-cancel" "app-env-region") => 1))

  (fact "that cancelling a deployment which doesn't have a cancellation registered returns false"
        (cancel-deployment cancel-deployment-params) => false
        (provided
         (car/srem "maestro:deployments:awaiting-cancel" "app-env-region") => 0))

  (fact "that obtaining the queue status works"
        (queue-status) => ..status..
        (provided
         (car-mq/queue-status anything "scheduled-tasks") => ..status..))

  (fact "that we are healthy if we don't get an exception while pinging Redis"
        (healthy?) => truthy
        (provided
         (car/ping) => ..anything..))

  (fact "that we aren't healthy if we get an exception while pinging Redis"
        (healthy?) => falsey
        (provided
         (car/ping) =throws=> (ex-info "Busted" {}))))
