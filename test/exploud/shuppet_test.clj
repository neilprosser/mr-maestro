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
       (http/simple-post anything {:socket-timeout 180000}) =>  {:status 201
                                                                 :body (json/generate-string dummy-app)}))

(fact "that upserting an application throws an error if shuppet returns bad status."
      (upsert-application ..name..) => (throws ExceptionInfo)
      (provided
       (http/simple-post anything {:socket-timeout 180000}) =>  {:status 500
                                                                 :body ..body..}))

(fact "apply fails when shuppet fails to return environment list"
      (apply-config ..name..) => (throws ExceptionInfo)
      (provided
       (http/simple-get anything {:socket-timeout 180000}) => {:status 500}))

(fact "apply fails when shuppet fails to apply a configuration"
      (apply-config ..name..) => (throws ExceptionInfo)
      (provided
       (envs-url) => ..envs-url..
       (apply-url anything ..name..) => ..apply-url..
       (http/simple-get ..envs-url.. {:socket-timeout 180000}) => {:status 200
                                                                   :body "{\"environments\":[\"poke\"]}"}
       (http/simple-get ..apply-url.. {:socket-timeout 180000}) => {:status 500}))

(fact "responses are returned when shuppet applies a config"
      (apply-config ..name..) => [{:status 200}]
      (provided
       (envs-url) => ..envs-url..
       (apply-url anything ..name..) => ..apply-url..
       (http/simple-get ..envs-url.. {:socket-timeout 180000}) => {:status 200
                                                                   :body "{\"environments\":[\"poke\"]}"}
       (http/simple-get ..apply-url.. {:socket-timeout 180000}) => {:status 200}))
