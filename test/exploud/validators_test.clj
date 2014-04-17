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

(fact "that `valid-date?` is happy with nil input"
      (valid-date? nil)
      => truthy)

(fact "that `valid-date?` is happy with valid date"
      (valid-date? "2013-01-01")
      => truthy)

(fact "that `valid-date?` is unhappy with invalid date"
      (valid-date? "not a date")
      => falsey)
