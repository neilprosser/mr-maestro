(ns exploud.unit.onix
  (:require [cheshire.core :as json]
            [exploud
             [http :as http]
             [onix :refer :all]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "that when creating an application we send the right thing to Onix"
      (create-application "app-name")
      => ..application..
      (provided
       (json/generate-string {:name "app-name"})
       => ..body..
       (http/simple-post
        "http://onix:8080/1.x/applications"
        {:content-type :json
         :body ..body..})
       => {:status 201
           :body ..response-json..}
       (json/parse-string ..response-json.. true)
       => ..application..))

(fact "that when creating an application and Onix gives a non-201 status we throw an exception"
      (create-application "app-name")
      => (throws ExceptionInfo "Unexpected status while creating application")
      (provided
       (json/generate-string {:name "app-name"})
       => ..body..
       (http/simple-post
        "http://onix:8080/1.x/applications"
        {:content-type :json
         :body ..body..})
       => {:status 500}))

(fact "that when getting an application we do the right thing in the happy case"
      (application "app-name")
      => ..application..
      (provided
       (http/simple-get
        "http://onix:8080/1.x/applications/app-name")
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => ..application..))

(fact "that when getting an application that doesn't exist we get `nil`"
      (application "app-name")
      => nil
      (provided
       (http/simple-get
        "http://onix:8080/1.x/applications/app-name")
       => {:status 404}))

(fact "that when upserting an application that doesn't exist we create it"
      (upsert-application "app-name")
      => ..application..
      (provided
       (application "app-name")
       => nil
       (create-application "app-name")
       => ..application..))

(fact "that when upserting an application we don't create it if it's already updated"
      (upsert-application "app-name")
      => ..application..
      (provided
       (application "app-name")
       => ..application..))
