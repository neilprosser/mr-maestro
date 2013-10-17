(ns exploud.unit.web
  (:require [cheshire.core :as json]
            [exploud.web :refer :all]
            [midje.sweet :refer :all]))

(defn request
  [method resource & {:as others}]
  (routes (merge {:request-method method
                  :uri resource} (update-in others [:body]
                                            #(java.io.ByteArrayInputStream.
                                              (.getBytes (json/generate-string %)))))))

(defn request [method resource]
  (routes {:request-method method
           :uri resource } ))

(fact-group
            (fact "Ping returns a pong"
                  (:body (request :get "/1.x/ping"))  => "pong" ))
