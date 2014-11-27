(ns exploud.responses
  (:require [clojure.tools.logging :refer [warn]]
            [exploud.log :as log])
  (:import (com.amazonaws AmazonServiceException)))

(def retry-exception-backoff-millis
  5000)

(defn retry-after
  [millis]
  {:status :retry
   :backoff-ms millis})

(defn success
  [parameters]
  {:status :success
   :parameters parameters})

(defn- aws-request-limit-exceeded?
  [e]
  (and (= AmazonServiceException (type e))
       (= "RequestLimitExceeded" (.getErrorCode e))))

(defn- aws-throttling?
  [e]
  (and (= AmazonServiceException (type e))
       (= "Throttling" (.getErrorCode e))))

(defn- unexpected-response?
  [e]
  (= :exploud.pedantic/unexpected-response (:type (ex-data e))))

(defn- retryable-error?
  [e]
  (or (aws-request-limit-exceeded? e)
      (aws-throttling? e)
      (unexpected-response? e)))

(defn error-with
  [e]
  (if (retryable-error? e)
    (do
      (warn e "Retryable exception encountered")
      (log/write "Retryable exception encountered, logs will contain more information.")
      (retry-after retry-exception-backoff-millis))
    {:status :error
     :throwable e}))

(defn capped-retry-after
  [millis attempt max-attempts]
  (if (<= max-attempts attempt)
    (error-with (ex-info "Maximum number of attempts has been reached." {}))
    (retry-after millis)))
