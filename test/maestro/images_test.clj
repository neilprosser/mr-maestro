(ns maestro.images-test
  (:require [maestro
             [elasticsearch :as es]
             [environments :as environments]
             [images :refer :all]]
            [midje.sweet :refer :all]))

(fact "that getting the last completed deployment image works"
      (last-two-completed-deployment-images "application" "environment" "region")
      => ["image"]
      (provided
       (es/get-deployments {:application "application"
                            :environment "environment"
                            :from 0
                            :region "region"
                            :size 2
                            :status "completed"})
       => [{:new-state {:image-details {:id "image"}}}]))

(fact "that we can obtain the prohibited images list across all known environments"
      (prohibited-images "application" "region")
      => #{"ami-1" "ami-2"}
      (provided
       (environments/environments) => {:e1 {} :e2 {} :e3 {} :e4 {}}
       (last-two-completed-deployment-images "application" "e1" "region") => ["ami-1" "ami-2"]
       (last-two-completed-deployment-images "application" "e2" "region") => ["ami-2"]
       (last-two-completed-deployment-images "application" "e3" "region") => []
       (last-two-completed-deployment-images "application" "e4" "region") => nil))
