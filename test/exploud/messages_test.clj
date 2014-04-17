(ns exploud.messages-test
  (:require [exploud.messages :refer :all]
            [midje.sweet :refer :all]))

(fact "that getting the next action works"
      (action-after :exploud.messages.data/create-names)
      => :exploud.messages.data/get-image-details)
