(ns maestro.messages.healthy-test
  (:require [maestro.healthy :as healthy]
            [maestro.messages.healthy :refer :all]
            [midje.sweet :refer :all]))

(def register-with-healthy-params
  {:environment "environment"
   :new-state {:auto-scaling-group-name "new-asg"
               :tyranitar {:application-properties {:service.port "1234"}
                           :deployment-params {:health-check-type "EC2"
                                               :healthy {:scheme "scheme"
                                                         :path "path"
                                                         :port "2345"
                                                         :timeout 100}}}}
   :region "region"})

(fact "that we don't register anything with Healthy if we've not asked for it"
      (let [params (assoc-in register-with-healthy-params [:new-state :tyranitar :deployment-params :healthy] nil)]
        (register-with-healthy {:parameters params}) => (contains {:status :success})
        (provided
         (healthy/register-auto-scaling-group "environment" "region" "new-asg" anything anything anything anything) => nil :times 0)))

(fact "that we register with Healthy if we've asked for it"
      (register-with-healthy {:parameters register-with-healthy-params}) => (contains {:status :success})
      (provided
       (healthy/register-auto-scaling-group "environment" "region" "new-asg" "path" "2345" "scheme" 100) => true))

(fact "that a failure while registering with Healthy is allowed"
      (register-with-healthy {:parameters register-with-healthy-params}) => (contains {:status :success})
      (provided
       (healthy/register-auto-scaling-group "environment" "region" "new-asg" "path" "2345" "scheme" 100) => false))

(fact "that we register with Healthy if we've asked for it but fallback to a port provided in the application properties if nothing is present in deployment params"
      (let [params (assoc-in register-with-healthy-params [:new-state :tyranitar :deployment-params :healthy :port] nil)]
        (register-with-healthy {:parameters params}) => (contains {:status :success})
        (provided
         (healthy/register-auto-scaling-group "environment" "region" "new-asg" "path" "1234" "scheme" 100) => true)))

(def deregister-from-healthy-params
  {:environment "environment"
   :previous-state {:auto-scaling-group-name "previous-asg"
                    :tyranitar {:deployment-params {:health-check-type "EC2"
                                                    :healthy {:scheme "scheme"
                                                              :path "path"
                                                              :port "2345"
                                                              :timeout 100}}}}
   :region "region"})

(fact "that we don't deregister from Healthy if the previous state wasn't using Healthy"
      (let [params (assoc-in deregister-from-healthy-params [:previous-state :tyranitar :deployment-params :healthy] nil)]
        (deregister-from-healthy {:parameters params}) => (contains {:status :success})
        (provided
         (healthy/deregister-auto-scaling-group anything anything anything) => nil :times 0)))

(fact "that we don't deregister from Healthy if there's nothing which needs deregistering"
      (let [params (assoc-in deregister-from-healthy-params [:previous-state :auto-scaling-group-name] nil)]
        (deregister-from-healthy {:parameters params}) => (contains {:status :success})
        (provided
         (healthy/deregister-auto-scaling-group anything anything anything) => nil :times 0)))

(fact "that we deregister from Healthy if we've been told to"
      (deregister-from-healthy {:parameters deregister-from-healthy-params}) => (contains {:status :success})
      (provided
       (healthy/deregister-auto-scaling-group "environment" "region" "previous-asg") => true))

(fact "that a failure deregistering from from Healthy is allowed"
      (deregister-from-healthy {:parameters deregister-from-healthy-params}) => (contains {:status :success})
      (provided
       (healthy/deregister-auto-scaling-group "environment" "region" "previous-asg") => false))
