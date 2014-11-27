(ns exploud.numel-test
  (:require [cheshire.core :as json]
            [exploud
             [environments :as environments]
             [http :as http]
             [numel :refer :all]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "that getting application registrations for prod uses the correct url"
      (application-registrations "prod" "application")
      => ..json..
      (provided
       (environments/prod-account? "prod") => true
       (http/simple-get "http://numelprod/1.x/registrations/prod/application" {:socket-timeout 10000}) => {:body ..body..
                                                                                                                :status 200}
       (json/parse-string ..body.. true) => ..json..))

(fact "that getting application registrations for a non-prod environment uses the correct url"
      (application-registrations "anything" "application")
      => ..json..
      (provided
       (environments/prod-account? "anything") => false
       (http/simple-get "http://numelpoke/1.x/registrations/anything/application" {:socket-timeout 10000}) => {:body ..body..
                                                                                                                    :status 200}
       (json/parse-string ..body.. true) => ..json..))

(fact "that getting a non-200 status throws an exception"
      (application-registrations "anything" "application")
      => (throws ExceptionInfo "Unexpected response")
      (provided
       (environments/prod-account? "anything") => false
       (http/simple-get "http://numelpoke/1.x/registrations/anything/application" {:socket-timeout 10000}) => {:body ..body..
                                                                                                                    :status 500}))
