(ns exploud.util_test
  (:require [clj-time.core :as time]
            [exploud.util :refer :all]
            [midje.sweet :refer :all]))

(fact "given a collection `list-from` gives back the collection"
      (list-from ["hello" "world"])
      => ["hello" "world"])

(fact "given a non-collection `list-from` gives back the thing as a collection"
      (list-from "hello")
      => ["hello"])

(fact "given a single nil `list-from` gives back an empty list"
      (list-from nil)
      => [])

(fact "that string->number doesn't blow up if given nil"
      (string->number nil)
      => nil)

(fact "that string->number doesn't blow up if given a number"
      (string->number 1232)
      => 1232)

(fact "that string->number turns a numeric string into a number"
      (string->number "1232")
      => 1232)

(fact "that string->number turns a non-numeric string into nil"
      (string->number "adsfsa")
      => nil)

(fact "that strip-first-forward-slash works if the string starts with /"
      (strip-first-forward-slash "/this/that")
      => "this/that")

(fact "that strip-first-forward-slash works if the string doesn't start with /"
      (strip-first-forward-slash "this/that")
      => "this/that")

(fact "that appending to a non-existent `:log` creates a new one"
      (append-to-task-log "Some message" {})
      => {:log [{:date ..date.. :message "Some message"}]}
      (provided
       (time/now)
       => ..date..))

(fact "that appending to a nil `:log` creates a new one"
      (append-to-task-log "Some message" {:log nil})
      => {:log [{:date ..date.. :message "Some message"}]}
      (provided
       (time/now)
       => ..date..))

(fact "that appending to an already existing `:log` puts the message at the end"
      (append-to-task-log "Some message" {:log [{:date ..first-date.. :message "First thing"}]})
      => {:log [{:date ..first-date.. :message "First thing"} {:date ..second-date.. :message "Some message"}]}
      (provided
       (time/now)
       => ..second-date..))
