(ns exploud.actions
  (:require [clojure.contrib.math :refer [expt]]
            [clojure.tools.logging :refer [debug info warn]])
  (:import com.amazonaws.AmazonServiceException))

(def ^:private retries-allowed
  4)

(defn- sleep-millis
  [attempts-left]
  (->> attempts-left
       (- retries-allowed)
       (expt 2)
       (* 100)))

(defn- sleep
  [millis]
  (debug "Sleeping for" millis "ms")
  (Thread/sleep millis))

(defn retry-on-throttling-error
  ([function]
     (retry-on-throttling-error retries-allowed function))
  ([attempts-left function]
     (let [attempts-left (min retries-allowed attempts-left)]
       (try
         (function)
         (catch AmazonServiceException e
           (warn e "Failure while talking to Amazon")
           (if-not (and (= (.getErrorCode e) "Throttling")
                        (pos? attempts-left))
             (throw e)
             (do
               (info "Retrying")
               (sleep (sleep-millis attempts-left))
               (retry-on-throttling-error (dec attempts-left) function))))))))
