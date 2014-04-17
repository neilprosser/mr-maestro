(ns exploud.shuppet-test
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
      (apply-config "app") => (throws ExceptionInfo)
      (provided
       (http/simple-get "http://shuppet:8080/1.x/envs" {:socket-timeout 180000}) => {:status 200
                                                                                     :body "{\"environments\":[\"poke\"]}"}
       (http/simple-get "http://shuppet:8080/1.x/envs/poke/apps/app/apply" {:socket-timeout 180000}) => {:status 500}))

(fact "responses are returned when shuppet applies a config"
      (apply-config "app") => [{:status 200}]
      (provided
       (http/simple-get "http://shuppet:8080/1.x/envs" {:socket-timeout 180000}) => {:status 200
                                                                                     :body "{\"environments\":[\"poke\"]}"}
       (http/simple-get "http://shuppet:8080/1.x/envs/poke/apps/app/apply" {:socket-timeout 180000}) => {:status 200}))

(fact "that getting configuration does the right thing for the happy path"
      (configuration "environment" "application") => ..configuration..
      (provided
       (http/simple-get "http://shuppet:8080/1.x/envs/environment/apps/application" {:socket-timeout 180000})
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => ..configuration..))

(fact "that getting configuration does the right thing for the missing path"
      (configuration "environment" "application") => nil
      (provided
       (http/simple-get "http://shuppet:8080/1.x/envs/environment/apps/application" {:socket-timeout 180000})
       => {:status 404}))

(fact "that getting configuration does the right thing for the sad path"
      (configuration "environment" "application") => (throws ExceptionInfo "Unexpected status while getting configuration for application and environment")
      (provided
       (http/simple-get "http://shuppet:8080/1.x/envs/environment/apps/application" {:socket-timeout 180000})
       => {:status 500}))
