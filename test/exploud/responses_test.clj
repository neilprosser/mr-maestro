(ns exploud.responses-test
  (:require [exploud.responses :refer :all]
            [midje.sweet :refer :all]))

(fact "that creating an error for request-limit-exceeded turns it into a retry"
      (def e (doto (com.amazonaws.AmazonServiceException. "Failed")
                    (.setServiceName "AmazonEC2")
                    (.setRequestId (str (java.util.UUID/randomUUID)))
                    (.setErrorCode "RequestLimitExceeded")
                    (.setStatusCode 503)))
      (error-with e) => {:status :retry
                         :backoff-ms 5000})

(fact "that creating an error for throttling turns it into a retry"
      (def e (doto (com.amazonaws.AmazonServiceException. "Failed")
                    (.setServiceName "AmazonAutoScaling")
                    (.setRequestId (str (java.util.UUID/randomUUID)))
                    (.setErrorCode "Throttling")
                    (.setStatusCode 400)))
      (error-with e) => {:status :retry
                         :backoff-ms 5000})

(fact "that creating an error with an exception which should retry is still an error"
      (def e (doto (com.amazonaws.AmazonServiceException. "Failed")
                    (.setServiceName "AmazonEC2")
                    (.setRequestId (str (java.util.UUID/randomUUID)))
                    (.setErrorCode "RequestLimitNotExceeded")
                    (.setStatusCode 503)))
      (error-with e) => {:status :error
                         :throwable e})

(fact "that creating an error for an expected Shuppet response turns it into a retry"
      (error-with (ex-info "Busted" {:type :exploud.shuppet/unexpected-response})) => {:status :retry
                                                                                       :backoff-ms 5000})

(fact "that creating an error with an exception which doesn't have the right properties is still an error"
      (def e (java.lang.Exception. "Failed"))
      (error-with e) => {:status :error
                         :throwable e})

(fact "that creating an error response with an exception works"
      (error-with ..exception..) => {:status :error
                                     :throwable ..exception..})

(fact "that creating a retry response with timeout works"
      (retry-after 10) => {:status :retry
                           :backoff-ms 10})

(fact "that creating a capped retry response works if we're below the maximum attempts"
      (capped-retry-after 15 1 2) => {:status :retry
                                      :backoff-ms 15})

(fact "that creating a capped retry response gives an error if we're on the maximum attempts"
      (capped-retry-after 16 2 2) => (contains {:status :error}))

(fact "that creating a capped retry response gives an error if we're somehow above the maximum attempts"
      (capped-retry-after 12 3 2) => (contains {:status :error}))

(fact "that creating a success response works"
      (success ..parameters..) => {:status :success
                                   :parameters ..parameters..})
