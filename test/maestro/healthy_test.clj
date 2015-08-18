(ns maestro.healthy-test
  (:require [cheshire.core :as json]
            [maestro
             [environments :as environments]
             [healthy :refer :all]
             [http :as http]]
            [midje.sweet :refer :all]))

(fact "that attempting to register an auto-scaling group for an environment with no Healthy URL does nothing"
      (register-auto-scaling-group "environment" "region" "asg" "path" "port" "scheme" "timeout") => false
      (provided
       (environments/healthy-url "environment") => nil
       (http/simple-put anything anything) => nil :times 0))

(fact "that registering an auto-scaling group calls through to Healthy correctly"
      (register-auto-scaling-group "environment" "region" "asg" "path" "port" "scheme" "timeout") => true
      (provided
       (environments/healthy-url "environment") => "http://healthy"
       (json/generate-string {:path "path"
                              :port "port"
                              :scheme "scheme"
                              :timeout "timeout"}) => ..json..
       (http/simple-put "http://healthy/1.x/monitors/regions/region/asgs/asg" {:body ..json..
                                                                               :headers {"Content-Type" "application/json"}}) => {:status 201}))

(fact "that nil values are removed from the body when registering"
      (register-auto-scaling-group "environment" "region" "asg" "path" "port" nil nil) => true
      (provided
       (environments/healthy-url "environment") => "http://healthy"
       (json/generate-string {:path "path"
                              :port "port"}) => ..json..
       (http/simple-put "http://healthy/1.x/monitors/regions/region/asgs/asg" {:body ..json..
                                                                               :headers {"Content-Type" "application/json"}}) => {:status 201}))

(fact "that failing to register an auto-scaling group with Healthy returns false"
      (register-auto-scaling-group "environment" "region" "asg" "path" "port" "scheme" "timeout") => false
      (provided
       (environments/healthy-url "environment") => "http://healthy"
       (json/generate-string {:path "path"
                              :port "port"
                              :scheme "scheme"
                              :timeout "timeout"}) => ..json..
       (http/simple-put "http://healthy/1.x/monitors/regions/region/asgs/asg" {:body ..json..
                                                                               :headers {"Content-Type" "application/json"}}) => {:status 500}))

(fact "that an error while registering an auto-scaling group with Healthy returns false"
      (register-auto-scaling-group "environment" "region" "asg" "path" "port" "scheme" "timeout") => false
      (provided
       (environments/healthy-url "environment") => "http://healthy"
       (json/generate-string {:path "path"
                              :port "port"
                              :scheme "scheme"
                              :timeout "timeout"}) => ..json..
       (http/simple-put "http://healthy/1.x/monitors/regions/region/asgs/asg" {:body ..json..
                                                                               :headers {"Content-Type" "application/json"}}) =throws=> (ex-info "Busted" {})))

(fact "that attempting to deregister an auto-scaling group for an environment with no Healthy URL does nothing"
      (deregister-auto-scaling-group "environment" "region" "asg") => false
      (provided
       (environments/healthy-url "environment") => nil
       (http/simple-delete anything anything) => nil :times 0))

(fact "that deregistering an auto-scaling group calls through to Healthy correctly"
      (deregister-auto-scaling-group "environment" "region" "asg") => true
      (provided
       (environments/healthy-url "environment") => "http://healthy"
       (http/simple-delete "http://healthy/1.x/monitors/regions/region/asgs/asg") => {:status 204}))

(fact "that deregistering an auto-scaling group which Healthy doesn't know about is allowed"
      (deregister-auto-scaling-group "environment" "region" "asg") => true
      (provided
       (environments/healthy-url "environment") => "http://healthy"
       (http/simple-delete "http://healthy/1.x/monitors/regions/region/asgs/asg") => {:status 404}))

(fact "that failing to deregister an auto-scaling group returns false"
      (deregister-auto-scaling-group "environment" "region" "asg") => false
      (provided
       (environments/healthy-url "environment") => "http://healthy"
       (http/simple-delete "http://healthy/1.x/monitors/regions/region/asgs/asg") => {:status 500}))

(fact "that an exception while deregistering an auto-scaling group returns false"
      (deregister-auto-scaling-group "environment" "region" "asg") => false
      (provided
       (environments/healthy-url "environment") => "http://healthy"
       (http/simple-delete "http://healthy/1.x/monitors/regions/region/asgs/asg") =throws=> (ex-info "Busted" {})))
