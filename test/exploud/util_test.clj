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
      (clojurize :somethingThatIsCamelCase) => :something-that-is-camel-case)

(fact "that generating an ID works"
      (string? (generate-id)) => truthy)

(fact "that removing nil values works"
      (remove-nil-values {:key1 "something"
                          :key2 false
                          :key3 nil}) => {:key1 "something"
                                          :key2 false})
