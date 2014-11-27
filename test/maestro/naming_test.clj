(ns maestro.naming-test
  (:require [clj-time.core :as time]
            [maestro.naming :refer :all]
            [midje.sweet :refer :all]))

(fact "that creating a launch config name works"
      (create-launch-config-name {:application "application"
                                  :environment "environment"
                                  :iteration 23
                                  :date (time/date-time 2013 04 12 4 3 1)}) => "application-environment-v023-20130412040301")

(fact "that getting a new launch config name works"
      (new-launch-config-name {:application "application"
                               :environment "environment"
                               :iteration 12}) => "application-environment-v012-20110203040506"
                               (provided
                                (time/now) => (time/date-time 2011 2 3 4 5 6)))

(fact "that creating an ASG name works"
      (create-asg-name {:application "application"
                        :environment "environment"
                        :iteration 4}) => "application-environment-v004")

(fact "that extracting the information from a launch config name works"
      (launch-config-info "application-environment-v013-20140224175424") => {:application "application"
                                                                             :environment "environment"
                                                                             :iteration 13
                                                                             :date (time/date-time 2014 2 24 17 54 24)})

(fact "that extracting the information from an ASG name works"
      (asg-info "application-environment-v004") => {:application "application"
                                                    :environment "environment"
                                                    :iteration 4})

(fact "that extracting the information from an early ASG name works"
      (asg-info "application-environment") => {:application "application"
                                               :environment "environment"
                                               :iteration 0})

(fact "that creating the next ASG name works"
      (next-asg-name "application-environment-v045") => "application-environment-v046")

(fact "that getting the next ASG name for an early ASG works"
      (next-asg-name "application-environment") => "application-environment-v001")
