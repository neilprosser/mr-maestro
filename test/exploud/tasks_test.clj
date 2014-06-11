(ns exploud.tasks-test
  (:require [exploud
             [redis :as redis]
             [tasks :refer :all]]
            [midje.sweet :refer :all]))

(fact "that enqueuing a task does what we expect"
      (enqueue ..task..) => ..result..
      (provided
       (redis/enqueue ..task..) => ..result..))
