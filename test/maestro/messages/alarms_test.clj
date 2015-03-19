(ns maestro.messages.alarms-test
  (:require [amazonica.aws.cloudwatch :as cw]
            [maestro.messages.alarms :refer :all]
            [maestro
             [aws :as aws]
             [util :as util]]
            [midje.sweet :refer :all]))

(def create-cloudwatch-alarms-parameters
  {:environment "environment"
   :new-state {:auto-scaling-group-name "auto-scaling-group"
               :cloudwatch-alarms [{:alarm-name "one"} {:alarm-name "two"}]}
   :region "region"})

(fact "that we don't do anything if there are no alarms to create"
      (create-cloudwatch-alarms {:parameters {}}) => (contains {:status :success})
      (provided
       (cw/describe-alarms anything :alarm-name-prefix anything) => nil :times 0))

(fact "that an error while trying to describe alarms results in an error"
      (create-cloudwatch-alarms {:parameters create-cloudwatch-alarms-parameters}) => (contains {:status :error})
      (provided
       (cw/describe-alarms anything :alarm-name-prefix anything) =throws=> (ex-info "Busted" {})))

(fact "that an error while trying to create alarms results in an error"
      (create-cloudwatch-alarms {:parameters create-cloudwatch-alarms-parameters}) => (contains {:status :error})
      (provided
       (cw/describe-alarms anything :alarm-name-prefix anything) => {:metric-alarms []}
       (cw/put-metric-alarm anything anything anything) =throws=> (ex-info "Busted" {})))

(fact "that we create alarms if there are some to create"
      (create-cloudwatch-alarms {:parameters create-cloudwatch-alarms-parameters}) => (contains {:status :success})
      (provided
       (cw/describe-alarms anything :alarm-name-prefix "auto-scaling-group-") => {:metric-alarms []}
       (aws/config "environment" "region") => ..config..
       (util/to-params {:alarm-name "one"}) => [..one..]
       (util/to-params {:alarm-name "two"}) => [..two..]
       (cw/put-metric-alarm ..config.. ..one..) => nil
       (cw/put-metric-alarm ..config.. ..two..) => nil))

(fact "that we don't create alarms if they already exist"
      (create-cloudwatch-alarms {:parameters create-cloudwatch-alarms-parameters}) => (contains {:status :success})
      (provided
       (cw/describe-alarms anything :alarm-name-prefix "auto-scaling-group-") => {:metric-alarms [{:alarm-name "one"}]}
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
       (cw/describe-alarms anything :alarm-name-prefix anything) => nil :times 0))

(fact "that we don't do anything when there aren't any CloudWatch alarms for the old auto scaling group"
      (remove-old-cloudwatch-alarms {:parameters remove-old-cloudwatch-alarms-parameters}) => (contains {:status :success})
      (provided
       (cw/describe-alarms anything :alarm-name-prefix "old-auto-scaling-group-") => {:metric-alarms []}
       (cw/delete-alarms anything :alarm-names anything) => nil :times 0))

(fact "that we do the right thing when there are CloudWatch alarms for the old auto scaling group"
      (remove-old-cloudwatch-alarms {:parameters remove-old-cloudwatch-alarms-parameters}) => (contains {:status :success})
      (provided
       (cw/describe-alarms anything :alarm-name-prefix "old-auto-scaling-group-") => {:metric-alarms [{:alarm-name "alarm-one"} {:alarm-name "alarm-two"}]}
       (cw/delete-alarms anything :alarm-names ["alarm-one" "alarm-two"]) => nil))

(fact "that an error while attempting to fetch the old alarms is handled"
      (remove-old-cloudwatch-alarms {:parameters remove-old-cloudwatch-alarms-parameters}) => (contains {:status :error})
      (provided
       (cw/describe-alarms anything :alarm-name-prefix "old-auto-scaling-group-") =throws=> (ex-info "Busted" {})))

(fact "that an error while attempting to delete the old alarms is handled"
      (remove-old-cloudwatch-alarms {:parameters remove-old-cloudwatch-alarms-parameters}) => (contains {:status :error})
      (provided
       (cw/describe-alarms anything :alarm-name-prefix "old-auto-scaling-group-") => {:metric-alarms [{:alarm-name "alarm-one"} {:alarm-name "alarm-two"}]}
       (cw/delete-alarms anything :alarm-names ["alarm-one" "alarm-two"]) =throws=> (ex-info "Busted" {})))
