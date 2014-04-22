(ns exploud.setup-test
  (:require [cheshire.core :as json]
            [clj-time.core :as time]
            [exploud.setup :refer :all]
            [midje.sweet :refer :all]))

(fact "that we configuration Joda correctly"
      (configure-joda)
      (json/generate-string (time/now)) => truthy)
