(ns exploud.pedantic-test
  (:require [cheshire.core :as json]
            [exploud
             [http :as http]
             [pedantic :refer :all]]
            [midje.sweet :refer :all])
  (:import [clojure.lang ExceptionInfo]))

(def dummy-app {:name "shtest"})

(fact "that upserting an application creates a new one if Pedantic responds OK."
      (upsert-application ..name..) => dummy-app
      (provided
       (http/simple-post anything {:socket-timeout 180000}) =>  {:status 201
                                                                 :body (json/generate-string dummy-app)}))

(fact "that upserting an application throws an error if Pedantic returns bad status."
      (upsert-application ..name..) => (throws ExceptionInfo)
      (provided
       (http/simple-post anything {:socket-timeout 180000}) =>  {:status 500
                                                                 :body ..body..}))

(fact "that apply fails when Pedantic fails to return environment list"
      (apply-config ..name..) => (throws ExceptionInfo)
      (provided
       (http/simple-get anything {:socket-timeout 15000}) => {:status 500}))

(fact "that apply fails when Pedantic fails to apply a configuration"
      (apply-config "app") => (throws ExceptionInfo)
      (provided
       (http/simple-get "http://pedantic/1.x/envs" {:socket-timeout 15000}) => {:status 200
                                                                                :body "{\"environments\":[\"poke\"]}"}
       (http/simple-get "http://pedantic/1.x/envs/poke/apps/app/apply" {:socket-timeout 180000}) => {:status 500}))

(fact "that responses are returned when Pedantic applies a config"
      (apply-config "app") => [{:status 200}]
      (provided
       (http/simple-get "http://pedantic/1.x/envs" {:socket-timeout 15000}) => {:status 200
                                                                                :body "{\"environments\":[\"poke\"]}"}
       (http/simple-get "http://pedantic/1.x/envs/poke/apps/app/apply" {:socket-timeout 180000}) => {:status 200}))

(fact "that getting configuration does the right thing for the happy path"
      (configuration "environment" "application") => ..configuration..
      (provided
       (http/simple-get "http://pedantic/1.x/envs/environment/apps/application" {:socket-timeout 15000})
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => ..configuration..))

(fact "that getting configuration does the right thing for the forbidden path"
      (configuration "environment" "application") => nil
      (provided
       (http/simple-get "http://pedantic/1.x/envs/environment/apps/application" {:socket-timeout 15000})
       => {:status 403}))

(fact "that getting configuration does the right thing for the missing path"
      (configuration "environment" "application") => nil
      (provided
       (http/simple-get "http://pedantic/1.x/envs/environment/apps/application" {:socket-timeout 15000})
       => {:status 404}))

(fact "that getting configuration does the right thing for the sad path"
      (configuration "environment" "application") => (throws ExceptionInfo "Unexpected status while getting configuration for application in environment.")
      (provided
       (http/simple-get "http://pedantic/1.x/envs/environment/apps/application" {:socket-timeout 15000})
       => {:status 500}))
