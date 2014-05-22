(ns exploud.util-test
  (:require [clj-time.core :as time]
            [exploud.util :refer :all]
            [midje.sweet :refer :all])
  (:import java.util.UUID))

(fact "that extracting details from an AMI name works"
      (ami-details "ent-exploud-0.19-1-2013-10-24_18-41-23")
      => {:image-name "ent-exploud-0.19-1-2013-10-24_18-41-23"
          :application "exploud"
          :version "0.19"
          :iteration "1"
          :bake-date (time/date-time 2013 10 24 18 41 23)})

(fact "that extracting details from an AMI name works if we're doing HVM stuff"
      (ami-details "hvm-ent-graphite-1.0-1-2014-05-22_13-02-05")
      => {:image-name "hvm-ent-graphite-1.0-1-2014-05-22_13-02-05"
          :application "graphite"
          :version "1.0"
          :iteration "1"
          :bake-date (time/date-time 2014 5 22 13 2 5)})

(fact "that extracting details from an AMI name gives nil if we're given garbage"
      (ami-details "absolutelynothinglikeanimage")
      => nil)

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

(fact "that clojurizing things works"
      (clojurize-keys {:somethingThatIsCamelCase "woo"}) => {:something-that-is-camel-case "woo"})

(fact "that generating an ID works"
      (string? (generate-id)) => truthy)

(fact "that removing nil values works"
      (remove-nil-values {:key1 "something"
                          :key2 false
                          :key3 nil}) => {:key1 "something"
                                          :key2 false})

(fact "that getting the previous state key is correct when undo isn't set"
      (previous-state-key {}) => :previous-state)

(fact "that getting the previous state key is correct when undo is set"
      (previous-state-key {:undo true}) => :new-state)

(fact "that getting the new state key is correct when undo isn't set"
      (new-state-key {}) => :new-state)

(fact "that getting the new state key is correct when undo is set"
      (new-state-key {:undo true}) => :previous-state)
