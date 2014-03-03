(ns exploud.onix_test
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

(fact "that when adding a property we do the right thing"
      (add-property "application" :property "some value")
      => nil
      (provided
       (json/generate-string {:value "some value"})
       => ..body..
       (http/simple-put
        "http://onix:8080/1.x/applications/application/property"
        {:content-type :json
         :body ..body..})
       => {:status 201}))

(fact "that when adding a property and getting an error we do what we're supposed to do"
      (add-property "application" "property" "some value")
      => (throws ExceptionInfo "Unexpected status while adding property")
      (provided
       (json/generate-string {:value "some value"})
       => ..body..
       (http/simple-put
        "http://onix:8080/1.x/applications/application/property"
        {:content-type :json
         :body ..body..})
       => {:status 500}))

(fact "that when getting an application we do the right thing in the happy case"
      (application "app-name")
      => ..details..
      (provided
       (http/simple-get
        "http://onix:8080/1.x/applications/app-name")
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => {:metadata ..details..}))

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

(fact "that getting applications works properly"
      (applications)
      => {:names ["name1" "name2"]}
      (provided
       (http/simple-get
        "http://onix:8080/1.x/applications")
       => {:status 200
           :body "{\"applications\":[\"name1\",\"name2\"]}"}))
