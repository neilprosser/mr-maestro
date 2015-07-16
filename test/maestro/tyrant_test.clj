(ns maestro.tyrant-test
  (:require [cheshire.core :as json]
            [maestro
             [http :as http]
             [tyrant :refer :all]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "that getting application properties does the right thing"
      (application-properties "environment" "application" "hash")
      => ..properties..
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application/hash/application-properties"
        {:socket-timeout 30000})
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => {:data ..properties..}))

(fact "that getting application properties which fails throws an exception"
      (application-properties "environment" "application" "hash")
      => (throws ExceptionInfo "Unexpected response")
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application/hash/application-properties"
        {:socket-timeout 30000})
       => {:status 503}))

(fact "that getting application properties which fails with a 500 throws an exception"
      (application-properties "environment" "application" "hash")
      => (throws ExceptionInfo "Error retrieving file content - is the JSON valid?")
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application/hash/application-properties"
        {:socket-timeout 30000})
       => {:status 500}))

(fact "that getting application config does the right thing"
      (application-config "environment" "application" "hash")
      => ..config..
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application/hash/application-config"
        {:socket-timeout 30000})
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => {:data ..config..}))

(fact "that getting application config which fails throws an exception"
      (application-config "environment" "application" "hash")
      => (throws ExceptionInfo "Unexpected response")
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application/hash/application-config"
        {:socket-timeout 30000})
       => {:status 503}))

(fact "that getting a non-existent application config returns nil"
      (application-config "environment" "application" "hash")
      => nil
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application/hash/application-config"
        {:socket-timeout 30000})
       => {:status 404}))

(fact "that getting application config which fails with a 500 throws an exception"
      (application-config "environment" "application" "hash")
      => (throws ExceptionInfo "Error retrieving file content - is the JSON valid?")
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application/hash/application-config"
        {:socket-timeout 30000})
       => {:status 500}))

(fact "that getting deployment params does the right thing"
      (deployment-params "environment" "application" "hash")
      => ..properties..
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application/hash/deployment-params"
        {:socket-timeout 30000})
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => {:data ..properties..}))

(fact "that getting deployment params which fails throws an exception"
      (deployment-params "environment" "application" "hash")
      => (throws ExceptionInfo "Unexpected response")
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application/hash/deployment-params"
        {:socket-timeout 30000})
       => {:status 503}))

(fact "that getting deployment params which fails with a 500 throws an exception"
      (deployment-params "environment" "application" "hash")
      => (throws ExceptionInfo "Error retrieving file content - is the JSON valid?")
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application/hash/deployment-params"
        {:socket-timeout 30000})
       => {:status 500}))

(fact "that getting launch data does the right thing"
      (launch-data "environment" "application" "hash")
      => ..properties..
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application/hash/launch-data"
        {:socket-timeout 30000})
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => {:data ..properties..}))

(fact "that getting launch data which fails throws an exception"
      (launch-data "environment" "application" "hash")
      => (throws ExceptionInfo "Unexpected response")
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application/hash/launch-data"
        {:socket-timeout 30000})
       => {:status 503}))

(fact "that getting launch data which fails with a 500 throws an exception"
      (launch-data "environment" "application" "hash")
      => (throws ExceptionInfo "Error retrieving file content - is the JSON valid?")
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application/hash/launch-data"
        {:socket-timeout 30000})
       => {:status 500}))

(fact "that getting commits does the right thing"
      (commits "environment" "application")
      => ..commits..
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application"
        {:socket-timeout 30000})
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => {:commits ..commits..}))

(fact "that getting the last commit gives back the first item in the commits response"
      (last-commit-hash "environment" "application")
      => "last-commit"
      (provided
       (http/simple-get
        "http://tyrant/applications/environment/application"
        {:socket-timeout 30000})
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => {:commits [{:hash "last-commit"}
                     {:hash "b-commit"}
                     {:hash "a-commit"}]}))

(fact "that verifying a hash works when the commit list contains that hash"
      (verify-commit-hash "environment" "application" "hash") => true
      (provided
       (commits "environment" "application") => [{:hash "not the hash"} {:hash "hash"}]))

(fact "that verifying a hash works when the commit doesn't contain that hash"
      (verify-commit-hash "environment" "application" "hash") => false
      (provided
       (commits "environment" "application") => [{:hash "not the hash"}]))

(fact "that verifying a hash and getting an error throws an exception"
      (verify-commit-hash "environment" "application" "hash")
      => (throws ExceptionInfo "Unexpected response")
      (provided
       (http/simple-get "http://tyrant/applications/environment/application" {:socket-timeout 30000}) => {:status 500}))

(fact "that creating an application does the right thing"
      (create-application "application")
      => ..application..
      (provided
       (json/generate-string {:name "application"})
       => ..application-body..
       (http/simple-post
        "http://tyrant/applications"
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
        "http://tyrant/applications"
        {:content-type :json
         :body ..application-body..
         :socket-timeout 180000})
       => {:status 500}))

(fact "that getting an application does the right thing"
      (application "application")
      => ..application..
      (provided
       (http/simple-get "http://tyrant/applications"
                        {:socket-timeout 30000})
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => {:applications {:application ..application..}}))

(fact "that getting an unexpected response when getting an application is an error"
      (application "application")
      => (throws ExceptionInfo "Unexpected response")
      (provided
       (http/simple-get "http://tyrant/applications"
                        {:socket-timeout 30000})
       => {:status 500}))

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
