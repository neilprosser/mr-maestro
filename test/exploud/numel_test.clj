(ns exploud.numel-test
  (:require [cheshire.core :as json]
            [exploud
             [http :as http]
             [numel :refer :all]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "that getting application registrations for prod uses the correct url"
      (application-registrations "prod" "application")
      => ..json..
      (provided
       (http/simple-get "http://numelprod:8080/1.x/registration/application" {:socket-timeout 10000}) => {:body ..body..
                                                                                                          :status 200}
       (json/parse-string ..body.. true) => ..json..))

(fact "that getting application registrations for something other than prod uses the correct url"
      (application-registrations "anything" "application")
      => ..json..
      (provided
       (http/simple-get "http://numelpoke:8080/1.x/registration/application" {:socket-timeout 10000}) => {:body ..body..
                                                                                                          :status 200}
       (json/parse-string ..body.. true) => ..json..))

(fact "that getting a non-200 status throws an exception"
      (application-registrations "anything" "application")
      => (throws ExceptionInfo "Unexpected response")
      (provided
       (http/simple-get "http://numelpoke:8080/1.x/registration/application" {:socket-timeout 10000}) => {:body ..body..
                                                                                                          :status 500}))
