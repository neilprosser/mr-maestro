(ns maestro.setup-test
  (:require [cheshire.core :as json]
            [clj-time.core :as time]
            [maestro.setup :refer :all]
            [midje.sweet :refer :all]))

(fact "that we configurate Joda correctly"
      (configure-joda)
      (json/generate-string (time/now)) => truthy)
