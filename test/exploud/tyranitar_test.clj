(ns exploud.tyranitar_test
  (:require [cheshire.core :as json]
            [exploud
             [http :as http]
             [tyranitar :refer :all]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "that getting application properties does the right thing"
      (application-properties "environment" "application" "hash")
      => ..properties..
      (provided
       (http/simple-get
        "http://tyranitar:8080/1.x/applications/environment/application/hash/application-properties")
       => {:status 200
           :body ..body..}
       (json/parse-string ..body..)
       => ..properties..))

(fact "that getting application properties which fails throws an exception"
      (application-properties "environment" "application" "hash")
      => (throws ExceptionInfo "Unexpected response")
      (provided
       (http/simple-get
        "http://tyranitar:8080/1.x/applications/environment/application/hash/application-properties")
       => {:status 500}))

(fact "that getting deployment params does the right thing"
      (deployment-params "environment" "application" "hash")
      => ..properties..
      (provided
       (http/simple-get
        "http://tyranitar:8080/1.x/applications/environment/application/hash/deployment-params")
       => {:status 200
           :body ..body..}
       (json/parse-string ..body..)
       => ..properties..))

(fact "that getting deployment params  which fails throws an exception"
      (deployment-params "environment" "application" "hash")
      => (throws ExceptionInfo "Unexpected response")
      (provided
       (http/simple-get
        "http://tyranitar:8080/1.x/applications/environment/application/hash/deployment-params")
       => {:status 500}))

(fact "that getting launch data does the right thing"
      (launch-data "environment" "application" "hash")
      => ..properties..
      (provided
       (http/simple-get
        "http://tyranitar:8080/1.x/applications/environment/application/hash/launch-data")
       => {:status 200
           :body ..body..}
       (json/parse-string ..body..)
       => ..properties..))

(fact "that getting launch data which fails throws an exception"
      (launch-data "environment" "application" "hash")
      => (throws ExceptionInfo "Unexpected response")
      (provided
       (http/simple-get
        "http://tyranitar:8080/1.x/applications/environment/application/hash/launch-data")
       => {:status 500}))

(fact "that getting commits does the right thing"
      (commits "environment" "application")
      => ..commits..
      (provided
       (http/simple-get
        "http://tyranitar:8080/1.x/applications/environment/application")
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => ..commits..))

(fact "that getting the last commit gives back the first item in the commits response"
      (last-commit-hash "environment" "application")
      => "last-commit"
      (provided
       (http/simple-get
        "http://tyranitar:8080/1.x/applications/environment/application")
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => {:commits [{:hash "last-commit"}
                     {:hash "b-commit"}
                     {:hash "a-commit"}]}))

(fact "that creating an application does the right thing"
      (create-application "application")
      => ..application..
      (provided
       (json/generate-string {:name "application"})
       => ..application-body..
       (http/simple-post
        "http://tyranitar:8080/1.x/applications"
        {:content-type :json
         :body ..application-body..
         :socket-timeout 180000})
       => {:status 201
           :body ..body..}
       (json/parse-string ..body.. true)
       => ..application..))

(fact "that creating an application throws an exception when given an unexpected response"
      (create-application "application")
      => (throws ExceptionInfo "Unexpected response")
      (provided
       (json/generate-string {:name "application"})
       => ..application-body..
       (http/simple-post
        "http://tyranitar:8080/1.x/applications"
        {:content-type :json
         :body ..application-body..
         :socket-timeout 180000})
       => {:status 500}))

(fact "that getting an application does the right thing"
      (application "application")
      => ..application..
      (provided
       (http/simple-get "http://tyranitar:8080/1.x/applications")
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => {:applications {:application ..application..}}))

(fact "that upserting an application creates the application if it doesn't exist"
      (upsert-application "application")
      => ..application..
      (provided
       (application "application")
       => nil
       (create-application "application")
       => ..application..))

(fact "that upserting an application does not create the application if it exists"
      (upsert-application "application")
      => ..application..
      (provided
       (application "application")
       => ..application..))
