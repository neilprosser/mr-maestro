(ns exploud.validators-test
  (:require [exploud.validators :refer :all]
            [midje.sweet :refer :all]))

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

(fact "that `valid-instance-type?` is happy with `nil`"
      (valid-instance-type? nil)
      => truthy)

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
