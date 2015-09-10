(ns maestro.validators-test
  (:require [bouncer.core :as b]
            [maestro
             [environments :as environments]
             [validators :refer :all]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "that allowed-instances throws up if we provide an unknown virtualisation type"
      (allowed-instances "whatever")
      => (throws ExceptionInfo "Unknown virtualisation type 'whatever'."))

(fact "that asking for paravirtual allowed instances gives the right set"
      (allowed-instances "para")
      => para-instance-types)

(fact "that asking for HVM allowed instances gives the right set"
      (allowed-instances "hvm")
      => hvm-instance-types)

(fact "that `zero-or-more?` is happy with nil input"
      (zero-or-more? nil)
      => truthy)

(fact "that `zero-or-more?` is happy with zero"
      (zero-or-more? 0)
      => truthy)

(fact "that `zero-or-more?` is happy with zero string"
      (zero-or-more? "0")
      => truthy)

(fact "that `zero-or-more?` is happy with postitive numeric string input"
      (zero-or-more? "23")
      => truthy)

(fact "that `zero-or-more?` is happy with positive numeric input"
      (zero-or-more? 23)
      => truthy)

(fact "that `zero-or-more?` is unhappy with negative numeric string input"
      (zero-or-more? "-1")
      => falsey)

(fact "that `zero-or-more?` is unhappy with negative numeric input"
      (zero-or-more? -1)
      => falsey)

(fact "that `positive?` is happy with nil input"
      (positive? nil)
      => truthy)

(fact "that `positive?` is unhappy with zero"
      (positive? 0)
      => falsey)

(fact "that `positive?` is unhappy with zero string"
      (positive? "0")
      => falsey)

(fact "that `positive?` is happy with postitive numeric string input"
      (positive? "23")
      => truthy)

(fact "that `positive?` is happy with positive numeric input"
      (positive? 23)
      => truthy)

(fact "that `positive?` is unhappy with negative numeric string input"
      (positive? "-1")
      => falsey)

(fact "that `positive?` is unhappy with negative numeric input"
      (positive? -1)
      => falsey)

(fact "that `positive?` is unhappy with letters"
      (positive? "a")
      => falsey)

(fact "that `valid-application?` is happy with nil"
      (valid-application? nil)
      => truthy)

(fact "that `valid-application? is happy with all letters"
      (valid-application? "application")
      => truthy)

(fact "that `valid-application?` is unhappy about something with characters which aren't letters"
      (valid-application? "hello-world")
      => falsey)

(fact "that `valid-date?` is happy with nil input"
      (valid-date? nil)
      => truthy)

(fact "that `valid-date?` is happy with valid date"
      (valid-date? "2013-01-01")
      => truthy)

(fact "that `valid-date?` is unhappy with invalid date"
      (valid-date? "not a date")
      => falsey)

(fact "that `valid-boolean?` is happy with true"
      (valid-boolean? "true")
      => truthy)

(fact "that `valid-boolean?` is happy with false"
      (valid-boolean? "false")
      => truthy)

(fact "that `valid-boolean?` is happy with nil"
      (valid-boolean? nil)
      => truthy)

(fact "that `valid-boolean?` is unhappy with garbage"
      (valid-boolean? "tfaafse")
      => falsey)

(fact "that `valid-hash?` is happy with nil"
      (valid-hash? nil)
      => truthy)

(fact "that `valid-hash?` is unhappy with something invalid"
      (valid-hash? "not a hash")
      => falsey)

(fact "that `valid-hash?` is happy with a valid hash"
      (valid-hash? "db0adbdcf61e4237e1d116834e185aa06cb682ff")
      => truthy)

(fact "that `valid-uuid?` is happy with nil"
      (valid-uuid? nil)
      => truthy)

(fact "that `valid-uuid?` is unhappy with something invalid"
      (valid-uuid? "hello")
      => falsey)

(fact "that `valid-uuid?` is happy with a valid UUID"
      (valid-uuid? "a7ceb675-dd1c-4e71-bde9-0bc44df714bf")
      => truthy)

(fact "that `valid-healthcheck-type?` is happy with `EC2`"
      (valid-healthcheck-type? "EC2")
      => truthy)

(fact "that `valid-healthcheck-type?` is happy with `ELB`"
      (valid-healthcheck-type? "ELB")
      => truthy)

(fact "that `valid-healthcheck-type?` is happy with `nil`"
      (valid-healthcheck-type? nil)
      => truthy)

(fact "that `valid-healthcheck-type?` is unhappy with garbage"
      (valid-healthcheck-type? "dsjksdjk")
      => falsey)

(fact "that `valid-instance-type?` is happy with a known instance type"
      (valid-instance-type? "m1.small")
      => truthy)

(fact "that `valid-instance-type?` is unhappy with `nil`"
      (valid-instance-type? nil)
      => falsey)

(fact "that `valid-instance-type?` is unhappy with garbage"
      (valid-instance-type? "adkjlasd")
      => falsey)

(fact "that `valid-availability-zone?` is happy with a known availability zone"
      (valid-availability-zone? "a")
      => truthy)

(fact "that `valid-availability-zone?` is happy with `nil`"
      (valid-availability-zone? nil)
      => truthy)

(fact "that `valid-availability-zone?` is unhappy with garbage"
      (valid-availability-zone? "dasdasds")
      => falsey)

(fact "that `valid-availability-zones?` is happy with `nil`"
      (valid-availability-zones? nil)
      => truthy)

(fact "that `valid-availbility-zones?` is happy with a single valid zone"
      (valid-availability-zones? ["a"])
      => truthy)

(fact "that `valid-availability-zones?` is happy with multiple valid zones"
      (valid-availability-zones? ["a" "b"])
      => truthy)

(fact "that `valid-availability-zones?` is unhappy with a single invalid zone"
      (valid-availability-zones? "daskd")
      => falsey)

(fact "that `valid-availability-zones?` is unhappy with an invalid zone alongside a valid one"
      (valid-availability-zones? ["a" "fkajdks"])
      => falsey)

(fact "that `valid-region?` is happy with `nil`"
      (valid-region? nil)
      => truthy)

(fact "that `valid-region?` is unhappy about an unknown region"
      (valid-region? "unknown")
      => falsey)

(fact "that `valid-region?` is happy with a known region"
      (valid-region? "eu-west-1")
      => truthy)

(fact "that `valid-subnet-purpose?` is happy with `nil`"
      (valid-subnet-purpose? nil)
      => truthy)

(fact "that `valid-subnet-purpose?` is happy with a valid subnet purpose"
      (valid-subnet-purpose? "mgmt")
      => truthy)

(fact "that `valid-subnet-purpose?` is unhappy with garbage"
      (valid-subnet-purpose? "akdjskdasjdkas")
      => falsey)

(fact "that `valid-termination-policy?` is happy with `nil`"
      (valid-termination-policy? nil)
      => truthy)

(fact "that `valid-termination-policy?` is happy with a valid termination policy"
      (valid-termination-policy? "ClosestToNextInstanceHour")
      => truthy)

(fact "that `valid-termination-policy?` is unhappy with garbage"
      (valid-termination-policy? "askjlkasjdks")
      => falsey)

(fact "that `known-environment?` is happy with nil"
      (known-environment? nil)
      => truthy)

(fact "that `known-environment?` is unhappy with something unknown"
      (known-environment? "unknown")
      => falsey
      (provided
       (environments/environments) => {}))

(fact "that `known-environment?` is happy with something known"
      (known-environment? "known")
      => truthy
      (provided
       (environments/environments) => {:known {}}))

(fact "that `known-status?` is happy with nil"
      (known-status? nil)
      => truthy)

(fact "that `known-status?` is unhappy with something unknown"
      (known-status? "unknown")
      => falsey)

(fact "that `known-status?` is happy with something known"
      (known-status? "running")
      => truthy)

(fact "that `valid-scheduled-actions?` is happy with a single good scheduled action"
      (valid-scheduled-actions? {:action-1 {:cron "hello"
                                            :desired-capacity 1
                                            :max 1
                                            :min 1}})
      => truthy)

(fact "that `valid-scheduled-actions?` is happy with multiple good scheduled actions"
      (valid-scheduled-actions? {:action-1 {:cron "1 2 3 4 5"
                                            :desired-capacity 1
                                            :max 1
                                            :min 1}
                                 :action-2 {:cron "world"
                                            :desired-capacity 1
                                            :max 1
                                            :min 1}})
      => truthy)

(fact "that `valid-scheduled-actions?` is happy with multiple good scheduled actions"
      (valid-scheduled-actions? {:action-1 {:cron "30 4 * * *"
                                            :desired-capacity 1
                                            :max 1
                                            :min 1}
                                 :action-2 {:cron "world"
                                            :desired-capacity 1
                                            :max 1
                                            :min 1}})
      => truthy)

(fact "that `valid-scheduled-actions?` is unhappy when a scheduled action is missing cron"
      (valid-scheduled-actions? {:action-1 {:desired-capacity 1
                                            :max 1
                                            :min 1}})
      => falsey)

(fact "that `valid-scheduled-actions?` is unhappy when a scheduled action is missing desired-capacity"
      (valid-scheduled-actions? {:action-1 {:cron "* * * * *"
                                            :max 1
                                            :min 1}})
      => falsey)

(fact "that `valid-scheduled-actions?` is unhappy when a scheduled action has a non-numeric desired-capacity"
      (valid-scheduled-actions? {:action-1 {:cron "* * * * *"
                                            :desired-capacity "a"
                                            :max 1
                                            :min 1}})
      => falsey)

(fact "that `valid-scheduled-actions?` is unhappy when a scheduled action is missing max"
      (valid-scheduled-actions? {:action-1 {:cron "* * * * *"
                                            :desired-capacity 1
                                            :min 1}})
      => falsey)

(fact "that `valid-scheduled-actions?` is unhappy when a scheduled action has a non-numeric max"
      (valid-scheduled-actions? {:action-1 {:cron "* * * * *"
                                            :desired-capacity 1
                                            :max "dasd"
                                            :min 1}})
      => falsey)

(fact "that `valid-scheduled-actions?` is unhappy when a scheduled action is missing min"
      (valid-scheduled-actions? {:action-1 {:cron "* * * * *"
                                            :desired-capacity 1
                                            :max 1}})
      => falsey)

(fact "that `valid-scheduled-actions?` is unhappy when a scheduled action has a non-numeric min"
      (valid-scheduled-actions? {:action-1 {:cron "* * * * *"
                                            :desired-capacity 1
                                            :max 1
                                            :min "asdasda"}})
      => falsey)

(def deployment-request
  {:ami "ami-cdea1270"
   :application "application"
   :environment "environment"
   :message "message"
   :user "user"})

(fact "that our valid deployment request passes validation"
      (first (b/validate deployment-request deployment-request-validators)) => falsey
      (provided
       (environments/environments) => {:environment {}}))

(fact "that validating a deployment request spots that we need an AMI"
      (first (b/validate (dissoc deployment-request :ami) deployment-request-validators)) => truthy
      (provided
       (environments/environments) => {:environment {}}))

(fact "that validating a deployment request spots that we need an application"
      (first (b/validate (dissoc deployment-request :application) deployment-request-validators)) => truthy
      (provided
       (environments/environments) => {:environment {}}))

(fact "that validating a deployment request spots that we need an environment"
      (first (b/validate (dissoc deployment-request :environment) deployment-request-validators)) => truthy)

(fact "that validating a deployment request spots that we're using an unknown environment"
      (first (b/validate deployment-request deployment-request-validators)) => truthy
      (provided
       (environments/environments) => {}))

(fact "that validating a deployment request spots that we need a message"
      (first (b/validate (dissoc deployment-request :message) deployment-request-validators)) => truthy
      (provided
       (environments/environments) => {:environment {}}))

(fact "that validating a deployment request spots that we need a user"
      (first (b/validate (dissoc deployment-request :user) deployment-request-validators)) => truthy
      (provided
       (environments/environments) => {:environment {}}))

(def deployment
  {:application "application"
   :environment "environment"
   :id "something"
   :message "Some message"
   :new-state {:image-details {:id "ami-012aefc3"}}
   :region "region"
   :user "user"})

(fact "that our valid deployment passes validation"
      (first (b/validate deployment deployment-validators)) => falsey
      (provided
       (environments/environments) => {:environment {}}))

(fact "that validating a deployment spots that we need an application"
      (first (b/validate (dissoc deployment :application) deployment-validators)) => truthy
      (provided
       (environments/environments) => {:environment {}}))

(fact "that validating a deployment spots that we need an environment"
      (first (b/validate (dissoc deployment :environment) deployment-validators)) => truthy)

(fact "that validating a deployment spots that we're using an unknown environment"
      (first (b/validate deployment deployment-validators)) => truthy
      (provided
       (environments/environments) => {}))

(fact "that validating a deployment spots that we need an ID"
      (first (b/validate (dissoc deployment :id) deployment-validators)) => truthy
      (provided
       (environments/environments) => {:environment {}}))

(fact "that validating a deployment spots that we need a message"
      (first (b/validate (dissoc deployment :message) deployment-validators)) => truthy
      (provided
       (environments/environments) => {:environment {}}))

(fact "that validating a deployment spots that we need an image ID"
      (first (b/validate (assoc-in deployment [:new-state :image-details :id] nil) deployment-validators)) => truthy
      (provided
       (environments/environments) => {:environment {}}))

(fact "that validating a deployment spots that we need a valid image ID"
      (first (b/validate (assoc-in deployment [:new-state :image-details :id] "ami-whatever") deployment-validators)) => truthy
      (provided
       (environments/environments) => {:environment {}}))

(fact "that validating a deployment spots that we need a region"
      (first (b/validate (dissoc deployment :region) deployment-validators)) => truthy
      (provided
       (environments/environments) => {:environment {}}))

(fact "that validating a deployment spots that we need a user"
      (first (b/validate (dissoc deployment :user) deployment-validators)) => truthy
      (provided
       (environments/environments) => {:environment {}}))

(def resize
  {:desired-capacity 2
   :max 3
   :min 1})

(fact "that validating a resize request spots that we need a desired-capacity"
      (first (b/validate (dissoc resize :desired-capacity) resize-request-validators)) => truthy)

(fact "that validating a resize request spots when desired-capacity isn't a number"
      (first (b/validate (assoc resize :desired-capacity "a") resize-request-validators)) => truthy)

(fact "that validating a resize request spots when desired-capacity is negative"
      (first (b/validate (assoc resize :desired-capacity -1) resize-request-validators)) => truthy)

(fact "that validating a resize request lets desired-capacity have a value of 0"
      (first (b/validate (assoc resize :desired-capacity 0) resize-request-validators)) => falsey)

(fact "that validating a resize request spots that we need a max"
      (first (b/validate (dissoc resize :max) resize-request-validators)) => truthy)

(fact "that validating a resize request spots when max isn't a number"
      (first (b/validate (assoc resize :max "a") resize-request-validators)) => truthy)

(fact "that validating a resize request spots when max is negative"
      (first (b/validate (assoc resize :max -1) resize-request-validators)) => truthy)

(fact "that validating a resize request lets max have a value of 0"
      (first (b/validate (assoc resize :max 0) resize-request-validators)) => falsey)

(fact "that validating a resize request spots that we need a min"
      (first (b/validate (dissoc resize :min) resize-request-validators)) => truthy)

(fact "that validating a resize request spots when min isn't a number"
      (first (b/validate (assoc resize :min "a") resize-request-validators)) => truthy)

(fact "that validating a resize request spots when min is negative"
      (first (b/validate (assoc resize :min -1) resize-request-validators)) => truthy)

(fact "that validating a resize request lets min have a value of 0"
      (first (b/validate (assoc resize :min 0) resize-request-validators)) => falsey)

(fact "that validating a valid resize request is all good"
      (first (b/validate resize resize-request-validators)) => falsey)
