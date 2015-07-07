(ns maestro.messages.alarms-test
  (:require [amazonica.aws.cloudwatch :as cw]
            [maestro
             [alarms :as alarms]
             [aws :as aws]
             [util :as util]]
            [maestro.messages.alarms :refer :all]
            [midje.sweet :refer :all]))

(fact "that swap-policy-arn does what it should when faced with a string"
      (swap-policy-arn {"bizzle" "arn2"} "arn1") => "arn1")

(fact "that swap-policy-arn does what it should when faced with a map"
      (swap-policy-arn {"bizzle" "arn2"} {:policy "bizzle"}) => "arn2")

(fact "that swap-policy-arns replaces maps with the relevant arn"
      (swap-policy-arns {"bizzle" "arn2"} ["arn1" {:policy "bizzle"}]) => ["arn1" "arn2"])

(fact "that ensure-action-arn works on all alarms"
      (ensure-action-arn {"bizzle" "arn2"} {:alarm-actions [{:policy "bizzle"}]
                                            :insufficient-data-actions [{:policy "bizzle"}]
                                            :ok-actions [{:policy "bizzle"}]})
      => {:alarm-actions ["arn2"]
          :insufficient-data-actions ["arn2"]
          :ok-actions ["arn2"]})

(fact "that ensure-action-arn ignores actions if there aren't any"
      (ensure-action-arn {"bizzle" "arn2"} {:alarm-actions []
                                            :ok-actions [{:policy "bizzle"}]})
      => {:alarm-actions []
          :ok-actions ["arn2"]})

(fact "that ensure-action-arns works through all alarms"
      (ensure-action-arns {:cloudwatch-alarms [{:alarm-actions [{:policy "bizzle"}]}
                                               {:alarm-actions [{:policy "bizzle"}]}
                                               {:alarm-actions ["arn1"]}]
                           :scaling-policy-arns {"bizzle" "arn2"}})
      => [{:alarm-actions ["arn2"]}
          {:alarm-actions ["arn2"]}
          {:alarm-actions ["arn1"]}])

(fact "that populating action ARNs amends the parameters correctly"
      (populate-action-arns {:parameters {:new-state {:cloudwatch-alarms [{:alarm-actions [{:policy "policy-1"} "arn2"]}]
                                                      :scaling-policy-arns {"policy-1" "arn1"}}}})
      => {:status :success
          :parameters {:new-state {:cloudwatch-alarms [{:alarm-actions ["arn1" "arn2"]}]
                                   :scaling-policy-arns {"policy-1" "arn1"}}}})

(fact "that populating action ARNs when there are no CloudWatch alarms leaves the parameters as they are"
      (populate-action-arns {:parameters {:new-state {:cloudwatch-alarms []
                                                      :scaling-policy-arns {"policy-1" "arn1"}}}})
      => {:status :success
          :parameters {:new-state {:cloudwatch-alarms []
                                   :scaling-policy-arns {"policy-1" "arn1"}}}}
      (populate-action-arns {:parameters {:new-state {:scaling-policy-arns {"policy-1" "arn1"}}}})
      => {:status :success
          :parameters {:new-state {:scaling-policy-arns {"policy-1" "arn1"}}}})

(def create-cloudwatch-alarms-parameters
  {:environment "environment"
   :new-state {:auto-scaling-group-name "auto-scaling-group"
               :cloudwatch-alarms [{:alarm-name "one"} {:alarm-name "two"}]}
   :region "region"})

(fact "that we don't do anything if there are no alarms to create"
      (create-cloudwatch-alarms {:parameters {:new-state {:cloudwatch-alarms nil}}}) => (contains {:status :success})
      (create-cloudwatch-alarms {:parameters {:new-state {:cloudwatch-alarms []}}}) => (contains {:status :success})
      (provided
       (alarms/alarms-for-auto-scaling-group anything anything anything) => nil :times 0))

(fact "that an error while trying to describe alarms results in an error"
      (create-cloudwatch-alarms {:parameters create-cloudwatch-alarms-parameters}) => (contains {:status :error})
      (provided
       (alarms/alarms-for-auto-scaling-group anything anything anything) =throws=> (ex-info "Busted" {})))

(fact "that an error while trying to create alarms results in an error"
      (create-cloudwatch-alarms {:parameters create-cloudwatch-alarms-parameters}) => (contains {:status :error})
      (provided
       (alarms/alarms-for-auto-scaling-group anything anything anything) => []
       (cw/put-metric-alarm anything anything anything) =throws=> (ex-info "Busted" {})))

(fact "that we create alarms if there are some to create"
      (create-cloudwatch-alarms {:parameters create-cloudwatch-alarms-parameters}) => (contains {:status :success})
      (provided
       (alarms/alarms-for-auto-scaling-group "environment" "region" "auto-scaling-group") => []
       (aws/config "environment" "region") => ..config..
       (util/to-params {:alarm-name "one"}) => [..one..]
       (util/to-params {:alarm-name "two"}) => [..two..]
       (cw/put-metric-alarm ..config.. ..one..) => nil
       (cw/put-metric-alarm ..config.. ..two..) => nil))

(fact "that we don't create alarms if they already exist"
      (create-cloudwatch-alarms {:parameters create-cloudwatch-alarms-parameters}) => (contains {:status :success})
      (provided
       (alarms/alarms-for-auto-scaling-group "environment" "region" "auto-scaling-group") => [{:alarm-name "one"}]
       (aws/config "environment" "region") => ..config..
       (util/to-params {:alarm-name "one"}) => [..one..] :times 0
       (util/to-params {:alarm-name "two"}) => [..two..]
       (cw/put-metric-alarm ..config.. ..one..) => nil :times 0
       (cw/put-metric-alarm ..config.. ..two..) => nil))

(def remove-old-cloudwatch-alarms-parameters
  {:environment "environment"
   :previous-state {:auto-scaling-group-name "old-auto-scaling-group"}
   :region "region"})

(fact "that we don't do anything when trying to remove old CloudWatch alarms if there is no old auto scaling group"
      (remove-old-cloudwatch-alarms {:parameters (assoc remove-old-cloudwatch-alarms-parameters :previous-state {})}) => (contains {:status :success})
      (provided
       (alarms/alarms-for-auto-scaling-group anything anything anything) => nil :times 0))

(fact "that we don't do anything when there aren't any CloudWatch alarms for the old auto scaling group"
      (remove-old-cloudwatch-alarms {:parameters remove-old-cloudwatch-alarms-parameters}) => (contains {:status :success})
      (provided
       (alarms/alarms-for-auto-scaling-group anything anything "old-auto-scaling-group") => []
       (cw/delete-alarms anything :alarm-names anything) => nil :times 0))

(fact "that we do the right thing when there are CloudWatch alarms for the old auto scaling group"
      (remove-old-cloudwatch-alarms {:parameters remove-old-cloudwatch-alarms-parameters}) => (contains {:status :success})
      (provided
       (alarms/alarms-for-auto-scaling-group anything anything "old-auto-scaling-group") => [{:alarm-name "alarm-one"} {:alarm-name "alarm-two"}]
       (cw/delete-alarms anything :alarm-names ["alarm-one" "alarm-two"]) => nil))

(fact "that an error while attempting to fetch the old alarms is handled"
      (remove-old-cloudwatch-alarms {:parameters remove-old-cloudwatch-alarms-parameters}) => (contains {:status :error})
      (provided
       (alarms/alarms-for-auto-scaling-group anything anything "old-auto-scaling-group") =throws=> (ex-info "Busted" {})))

(fact "that an error while attempting to delete the old alarms is handled"
      (remove-old-cloudwatch-alarms {:parameters remove-old-cloudwatch-alarms-parameters}) => (contains {:status :error})
      (provided
       (alarms/alarms-for-auto-scaling-group anything anything "old-auto-scaling-group") =>[{:alarm-name "alarm-one"} {:alarm-name "alarm-two"}]
       (cw/delete-alarms anything :alarm-names ["alarm-one" "alarm-two"]) =throws=> (ex-info "Busted" {})))
