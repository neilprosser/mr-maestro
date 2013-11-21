(ns exploud.shuppet_test
  (:require [cheshire.core :as json]
            [exploud
             [http :as http]
             [shuppet :refer :all]]
            [midje.sweet :refer :all])
  (:import [clojure.lang ExceptionInfo]))

(def dummy-app {:name "shtest",
                :path "ssh://snc@source.nokia.com/shuppet/git/shtest",
                :branches ["prod"]})


(fact "that upserting an application creates a new one if shuppet responds OK."
      (upsert-application ..name..) => dummy-app
      (provided
       (get-application ..name..) => nil
       (http/simple-post anything) =>  {:status 201
                                        :body (json/generate-string dummy-app)}))

(fact "that upserting an application throws an error if shuppet returns bad status."
         (upsert-application ..name..) => (throws ExceptionInfo)
         (provided
          (get-application ..name..) => nil
          (http/simple-post anything) =>  {:status 500
                                           :body ..body..}))

(fact "that upserting an application creates a new one if it didn't exist."
      (upsert-application ..name..) => ..application..
      (provided
       (get-application ..name..) => nil
       (create-application ..name..) => ..application..))

(fact "that upserting an application fetches the existing one if it exists."
      (upsert-application ..name..) => ..application..
      (provided
       (get-application ..name..) => ..application..))
