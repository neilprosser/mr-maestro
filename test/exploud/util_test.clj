(ns exploud.util_test
  (:require [clj-time.core :as time]
            [exploud.util :refer :all]
            [midje.sweet :refer :all]))

(fact "that extracting details from an AMI name works"
      (ami-details "ent-exploud-0.19-1-2013-10-24_18-41-23")
      => {:name "exploud"
          :version "0.19"
          :iteration "1"
          :bake-date (time/date-time 2013 10 24 18 41 23)})

(fact "given a collection `list-from` gives back the collection"
      (list-from ["hello" "world"])
      => ["hello" "world"])

(fact "given a non-collection `list-from` gives back the thing as a collection"
      (list-from "hello")
      => ["hello"])

(fact "given a single nil `list-from` gives back an empty list"
      (list-from nil)
      => [])

(fact "that string->int doesn't blow up if given nil"
      (string->int nil)
      => nil)

(fact "that string->int doesn't blow up if given a number"
      (string->int 1232)
      => 1232)

(fact "that string->int doesn't blow up if given a zero-padded number"
      (string->int "09")
      => 9)

(fact "that string->int turns a numeric string into a number"
      (string->int "1232")
      => 1232)

(fact "that string->int turns a non-numeric string into nil"
      (string->int "adsfsa")
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
