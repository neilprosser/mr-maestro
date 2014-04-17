(ns exploud.actions-test
  (:require [exploud.actions :refer :all]
            [midje.sweet :refer :all])
  (:import com.amazonaws.AmazonServiceException
           java.util.UUID))

(def exception
  (doto
      (AmazonServiceException. "Rate exceeded")
    (.setErrorCode "Throttling")
    (.setRequestId "3b7ae6c1-aec4-11e3-b3e5-b58fe9afe3b6")
    (.setServiceName "AmazonAutoScaling")
    (.setStatusCode 400)))

(defn fake-fn
  []
  1)

(fact "Retrying does all the good business"
      (retry-on-throttling-error 1 #(fake-fn "name")) => (throws AmazonServiceException)
      (provided
       (fake-fn "name") =throws=> exception :times 2))
