(ns exploud.responses-test
  (:require [exploud.responses :refer :all]
            [midje.sweet :refer :all]))

(fact "that creating an error response works"
      (error) => {:status :error})

(fact "that creating an error response with an exception works"
      (error-with ..exception..) => {:status :error
                                     :throwable ..exception..})

(fact "that creating a retry response works"
      (retry) => {:status :retry})

(fact "that creating a retry response with timeout works"
      (retry-after 10) => {:status :retry
                           :backoff-ms 10})

(fact "that creating a capped retry response works if we're below the maximum attempts"
      (capped-retry-after 15 1 2) => {:status :retry
                                      :backoff-ms 15})

(fact "that creating a capped retry response gives an error if we're on the maximum attempts"
      (capped-retry-after 16 2 2) => {:status :error})

(fact "that creating a capped retry response gives an error if we're somehow above the maximum attempts"
      (capped-retry-after 12 3 2) => {:status :error})

(fact "that creating a success response works"
      (success ..parameters..) => {:status :success
                                   :parameters ..parameters..})
