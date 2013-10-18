(ns exploud.unit.util
  (:use [exploud.util :refer :all]
        [midje.sweet :refer :all]))

(fact-group

 (fact "given a collection `list-from` gives back the collection"
       (list-from ["hello" "world"])
       => ["hello" "world"])

 (fact "given a non-collection `list-from` gives back the thing as a collection"
       (list-from "hello")
       => ["hello"]))
