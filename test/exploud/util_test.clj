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

(fact "that now-string works"
      (now-string)
      => (str ..now..)
      (provided
       (time/now)
       => ..now..))
