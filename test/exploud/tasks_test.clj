(ns exploud.tasks-test
  (:require [exploud
             [redis :as redis]
             [tasks :refer :all]]
            [midje.sweet :refer :all]))

(fact "Enqueuing a task does what we expect"
      (enqueue ..task..) => ..result..
      (provided
       (redis/enqueue ..task..) => ..result..))
