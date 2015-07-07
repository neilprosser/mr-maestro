(ns maestro.messages.policies-test
  (:require [maestro.messages.policies :refer :all]
            [maestro.policies :as policies]
            [midje.sweet :refer :all]))

(fact "that creating policies stores policy ARNs in the correct place"
      (create-scaling-policies {:parameters {:environment "environment"
                                             :new-state {:scaling-policies ..policies..}
                                             :region "region"}})
      => {:status :success
          :parameters {:environment "environment"
                       :new-state {:scaling-policies ..policies..
                                   :scaling-policy-arns ..policy-arns..}
                       :region "region"}}
      (provided
       (policies/create-policies "environment" "region" ..policies..) => ..policy-arns..))

(fact "that creating policies handles errors"
      (create-scaling-policies {:parameters {:environment "environment"
                                             :new-state {:scaling-policies ..policies..}
                                             :region "region"}})
      => (contains {:status :error})
      (provided
       (policies/create-policies "environment" "region" ..policies..) =throws=> (ex-info "Busted" {})))
