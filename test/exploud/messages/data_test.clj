(ns exploud.messages.data-test
  (:require [bouncer.core :as b]
            [clj-time.core :as time]
            [exploud
             [aws :as aws]
             [onix :as onix]
             [shuppet :as shuppet]
             [tyranitar :as tyr]
             [util :as util]
             [validators :as v]]
            [exploud.messages.data :refer :all]
            [midje.sweet :refer :all]))

(fact "that preparing a deployment moves to the correct task"
      (start-deployment-preparation {:parameters {:application "application" :environment "environment"}})
      => (contains {:status :success
                    :parameters {:application "application"
                                 :environment "environment"
                                 :phase "preparation"}}))

(fact "that validating our deployment succeeds when there's nothing wrong with it"
      (validate-deployment {:parameters ..deployment..}) => (contains {:status :success})
      (provided
       (b/validate ..deployment.. v/deployment-validators) => []))

(fact "that validating our deployment fails when there's something wrong with it"
      (validate-deployment {:parameters ..deployment..}) => (contains {:status :error})
      (provided
       (b/validate ..deployment.. v/deployment-validators) => ["busted"]))

(fact "that getting the Onix metadata fails when there is an error"
      (get-onix-metadata {:parameters {:application "application"}}) => (contains {:status :error})
      (provided
       (onix/application "application") =throws=> (ex-info "Broken" {})))

(fact "that getting the Onix metadata fails when there is no metadata"
      (get-onix-metadata {:parameters {:application "application"}}) => (contains {:status :error})
      (provided
       (onix/application "application") => nil))

(fact "that getting the Onix metadata works"
      (get-onix-metadata {:parameters {:application "application"}}) => (contains {:status :success
                                                                                   :parameters {:application "application"
                                                                                                :new-state {:onix ..onix..}}})
      (provided
       (onix/application "application") => ..onix..))

(fact "that ensuring the Tyranitar hash doesn't go to Tyranitar if the hash is present"
      (ensure-tyranitar-hash {:parameters {:application "application"
                                           :environment "environment"
                                           :new-state {:hash "hash"}}}) => {:status :success
                                                                            :parameters {:application "application"
                                                                                         :environment "environment"
                                                                                         :new-state {:hash "hash"}}})

(fact "that ensuring the Tyranitar hash goes to Tyranitar if the hash isn't present"
      (ensure-tyranitar-hash {:parameters {:application "application"
                                           :environment "environment"
                                           :new-state {}}})
      => {:status :success
          :parameters {:application "application"
                       :environment "environment"
                       :new-state {:hash "last-hash"}}}
      (provided
       (tyr/last-commit-hash "environment" "application") => "last-hash"))

(fact "that ensuring the Tyranitar hash is an error if there's an exception"
      (ensure-tyranitar-hash {:parameters {:application "application"
                                           :environment "environment"
                                           :new-state {}}})
      => (contains {:status :error})
      (provided
       (tyr/last-commit-hash "environment" "application") =throws=> (ex-info "Busted" {})))

(fact "that verifying the Tyranitar hash works"
      (verify-tyranitar-hash {:parameters {:application "application"
                                           :environment "environment"
                                           :new-state {:hash "hash"}}})
      => (contains {:status :success})
      (provided
       (tyr/verify-commit-hash "environment" "application" "hash") => true))

(fact "that verifying the Tyranitar hash is an error if it doesn't match"
      (verify-tyranitar-hash {:parameters {:application "application"
                                           :environment "environment"
                                           :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/verify-commit-hash "environment" "application" "hash") => false))

(fact "that an exception while verifying the Tyranitar hash is an error"
      (verify-tyranitar-hash {:parameters {:application "application"
                                           :environment "environment"
                                           :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/verify-commit-hash "environment" "application" "hash") =throws=> (ex-info "Busted" {})))

(fact "that getting no Tyranitar application properties is an error"
      (get-tyranitar-application-properties {:parameters {:application "application"
                                                          :environment "environment"
                                                          :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/application-properties "environment" "application" "hash") => nil))

(fact "that an error while getting Tyranitar application properties is an error"
      (get-tyranitar-application-properties {:parameters {:application "application"
                                                          :environment "environment"
                                                          :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/application-properties "environment" "application" "hash") =throws=> (ex-info "Busted" {})))

(fact "that getting Tyranitar application properties works"
      (get-tyranitar-application-properties {:parameters {:application "application"
                                                          :environment "environment"
                                                          :new-state {:hash "hash"}}})
      => {:status :success
          :parameters {:application "application"
                       :environment "environment"
                       :new-state {:hash "hash"
                                   :tyranitar {:application-properties {:tyranitar :properties}}}}}
      (provided
       (tyr/application-properties "environment" "application" "hash") => {:tyranitar :properties}))

(fact "that getting no Tyranitar deployment params is an error"
      (get-tyranitar-deployment-params {:parameters {:application "application"
                                                     :environment "environment"
                                                     :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/deployment-params "environment" "application" "hash") => nil))

(fact "that an error while getting Tyranitar deployment params is an error"
      (get-tyranitar-deployment-params {:parameters {:application "application"
                                                     :environment "environment"
                                                     :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/deployment-params "environment" "application" "hash") =throws=> (ex-info "Busted" {})))

(fact "that getting Tyranitar deployment params adds in defaults where a value hasn't been specified"
      (get-in (get-tyranitar-deployment-params {:parameters {:application "application"
                                                             :environment "environment"
                                                             :new-state {:hash "hash"}}}) [:parameters :new-state :tyranitar :deployment-params])
      => (contains {:default-cooldown 10
                    :desired-capacity 23})
      (provided
       (tyr/deployment-params "environment" "application" "hash") => {:desiredCapacity 23}))

(fact "that generating a validation message does what is required"
      (generate-validation-message {:something '("something has one problem" "something has another problem")
                                    :other ["other is broken"]})
      => "Validation result:\n* other is broken\n* something has one problem\n* something has another problem")

(fact "that validating Tyranitar deployment params is an error if the validation fails"
      (validate-deployment-params {:parameters {:new-state {:tyranitar {:deployment-params {:max "a"}}}}})
      => (contains {:status :error}))

(fact "that getting no Tyranitar launch data is an error"
      (get-tyranitar-launch-data {:parameters {:application "application"
                                               :environment "environment"
                                               :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/launch-data "environment" "application" "hash") => nil))

(fact "that validating Tyranitar deployment params is successful if the validation succeeds"
      (validate-deployment-params {:parameters {:new-state {:tyranitar {:deployment-params {}}}}})
      => (contains {:status :success}))

(fact "that an error while getting Tyranitar launch data is an error"
      (get-tyranitar-launch-data {:parameters {:application "application"
                                               :environment "environment"
                                               :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/launch-data "environment" "application" "hash") =throws=> (ex-info "Busted" {})))

(fact "that getting Tyranitar launch data works"
      (get-tyranitar-launch-data {:parameters {:application "application"
                                               :environment "environment"
                                               :new-state {:hash "hash"}}})
      => {:status :success
          :parameters {:application "application"
                       :environment "environment"
                       :new-state {:hash "hash"
                                   :tyranitar {:launch-data {:launch :data}}}}}
      (provided
       (tyr/launch-data "environment" "application" "hash") => {:launch :data}))

(fact "that a missing security group by ID is an error"
      (map-security-group-ids {:parameters {:environment "environment"
                                            :region "region"
                                            :new-state {:tyranitar {:deployment-params {:selected-security-groups ["group-1" "sg-group-2-id"]}}}}})
      => (contains {:status :error})
      (provided
       (aws/security-groups "environment" "region") => [{:group-id "sg-group-1-id"
                                                         :group-name "group-1"}]))

(fact "that a missing security group by name is an error"
      (map-security-group-ids {:parameters {:environment "environment"
                                            :region "region"
                                            :new-state {:tyranitar {:deployment-params {:selected-security-groups ["group-1" "group-2"]}}}}})
      => (contains {:status :error})
      (provided
       (aws/security-groups "environment" "region") => [{:group-id "sg-group-1-id"
                                                         :group-name "group-1"}]))
(fact "that all security groups present is a success"
      (map-security-group-ids {:parameters {:environment "environment"
                                            :region "region"
                                            :new-state {:tyranitar {:deployment-params {:selected-security-groups ["group-1" "sg-group-2-id"]}}}}})
      => (contains {:status :success
                    :parameters {:environment "environment"
                                 :region "region"
                                 :new-state {:selected-security-group-ids ["sg-group-1-id" "sg-group-2-id"]
                                             :tyranitar {:deployment-params {:selected-security-groups ["group-1" "sg-group-2-id"]}}}}})
      (provided
       (aws/security-groups "environment" "region") => [{:group-id "sg-group-1-id"
                                                         :group-name "group-1"}
                                                        {:group-id "sg-group-2-id"
                                                         :group-name "group-2"}]))

(fact "that an exception causes the task to error"
      (map-security-group-ids {:parameters {:environment "environment"
                                            :region "region"
                                            :new-state {:tyranitar {:deployment-params {:selected-security-groups ["group-1" "sg-group-2-id"]}}}}})
      => (contains {:status :error})
      (provided
       (aws/security-groups "environment" "region") =throws=> (ex-info "Busted" {})))

(fact "that populating previous state when a previous one doesn't exist succeeds"
      (populate-previous-state {:parameters {:application "application"
                                             :environment "environment"
                                             :region "region"}})
      => {:parameters {:application "application"
                       :environment "environment"
                       :region "region"}
          :status :success}
      (provided
       (aws/last-application-auto-scaling-group "application" "environment" "region") => nil))

(fact "that populating previous tyranitar application properties when no previous state exists succeeds"
      (populate-previous-tyranitar-application-properties {:parameters {:application "application"
                                                                        :environment "environment"}})
      => {:parameters {:application "application"
                       :environment "environment"}
          :status :success})

(fact "that getting previous image details when no previous state exists succeeds"
      (get-previous-image-details {:parameters {:environment "environment"
                                                :region "region"}})
      => {:parameters {:environment "environment"
                       :region "region"}
          :status :success})

(fact "that creating names for something without any previous state works"
      (create-names {:parameters {:application "application"
                                  :environment "environment"}})
      => (contains {:status :success
                    :parameters {:application "application"
                                 :environment "environment"
                                 :new-state {:launch-configuration-name "application-environment-v001-20140102030405"
                                             :auto-scaling-group-name "application-environment-v001"}}})
      (provided
       (time/now) => (time/date-time 2014 1 2 3 4 5)))

(fact "that getting image details fills in the correct values"
      (get-image-details {:parameters {:environment "environment"
                                       :region "region"
                                       :new-state {:image-details {:id "image-id"}}}})
      => (contains {:status :success
                    :parameters {:environment "environment"
                                 :region "region"
                                 :new-state {:image-details {:id "image-id"
                                                             :details "details"}}}})
      (provided
       (aws/image "image-id" "environment" "region") => {:name "image-name"}
       (util/image-details "image-name") => {:details "details"}))

(fact "that getting an exception while getting image details is an error"
      (get-image-details {:parameters {:environment "environment"
                                       :region "region"
                                       :new-state {:image-details {:id "image-id"}}}})
      => (contains {:status :error})
      (provided
       (aws/image "image-id" "environment" "region") =throws=> (ex-info "Busted" {})))

(fact "that getting the details of a missing image is an error"
      (get-image-details {:parameters {:environment "environment"
                                       :region "region"
                                       :new-state {:image-details {:id "image-id"}}}})
      => (contains {:status :error})
      (provided
       (aws/image "image-id" "environment" "region") => nil))

(fact "that checking whether the image and instance type are compatible results in success when they are"
      (check-instance-type-compatibility {:parameters {:new-state {:image-details {:virt-type "hvm"}
                                                                   :tyranitar {:deployment-params {:instance-type "i2.8xlarge"}}}}})
      => (contains {:status :success}))

(fact "that checking whether the image and instance type are compatible results in an error when they aren't"
      (check-instance-type-compatibility {:parameters {:new-state {:image-details {:virt-type "para"}
                                                                   :tyranitar {:deployment-params {:instance-type "t2.micro"}}}}})
      => (contains {:status :error}))

(fact "that checking Shuppet configuration succeeds when Shuppet configuration exists"
      (check-shuppet-configuration {:parameters {:application "application"
                                                 :environment "poke"}})
      => (contains {:status :success})
      (provided
       (shuppet/configuration "poke" "application") => {}))

(fact "that checking Shuppet configuration fails when Shuppet configuration doesn't exist"
      (check-shuppet-configuration {:parameters {:application "application"
                                                 :environment "poke"}})
      => (contains {:status :error})
      (provided
       (shuppet/configuration "poke" "application") => nil))

(fact "that checking Shuppet configuration retries when Shuppet throws up"
      (check-shuppet-configuration {:parameters {:application "application"
                                                 :environment "prod"}})
      => (contains {:status :retry})
      (provided
       (shuppet/configuration "prod" "application") =throws=> (ex-info "Busted" {:type :exploud.shuppet/unexpected-response})))

(fact "that checking Shuppet configuration ignores environments other than 'poke' or 'prod'"
      (check-shuppet-configuration {:parameters {:application "application"
                                                 :environment "environment"}})
      => (contains {:status :success})
      (provided
       (shuppet/configuration "environment" "application") => {} :times 0))

(fact "that checking for deleted load balancers removes previously used load balancers which no longer exist"
      (check-for-deleted-load-balancers {:parameters {:environment "environment"
                                                      :region "region"
                                                      :previous-state {:tyranitar {:deployment-params {:selected-load-balancers ["existing" "nonexistent"]}}}}})
      => {:parameters {:environment "environment"
                       :region "region"
                       :previous-state {:tyranitar {:deployment-params {:selected-load-balancers ["existing"]}}}}
          :status :success}
      (provided
       (aws/load-balancers-with-names "environment" "region" ["existing" "nonexistent"]) => {"existing" {}
                                                                                             "nonexistent" nil}))

(def populate-subnets-params
  {:environment "environment"
   :new-state {:tyranitar {:deployment-params {:subnet-purpose "internal"}}}
   :region "region"})

(fact "that populating subnets uses all subnets found when `selected-zones` has not been given"
      (:new-state (:parameters (populate-subnets {:parameters populate-subnets-params})))
      => (contains {:availability-zones ["regiona" "regionb"]
                    :selected-subnets ["1" "2"]})
      (provided
       (aws/subnets-by-purpose "environment" "region" "internal") => [{:subnet-id "1" :availability-zone "regiona"} {:subnet-id "2" :availability-zone "regionb"}]))

(fact "that populating subnets correctly filters the subnets based on the contents of `availability-zones` when `selected-zones` has been given"
      (:new-state (:parameters (populate-subnets {:parameters (assoc-in populate-subnets-params [:new-state :tyranitar :deployment-params :selected-zones] ["b"])})))
      => (contains {:availability-zones ["regionb"]
                    :selected-subnets ["2"]})
      (provided
       (aws/subnets-by-purpose "environment" "region" "internal") => [{:subnet-id "1" :availability-zone "regiona"} {:subnet-id "2" :availability-zone "regionb"}]))

(fact "that populating subnets gives an error when the subnets cannot match the provided demands of `selected-zones`"
      (populate-subnets {:parameters (assoc-in populate-subnets-params [:new-state :tyranitar :deployment-params :selected-zones] ["a" "c"])})
      => (contains {:status :error})
      (provided
       (aws/subnets-by-purpose "environment" "region" "internal") => [{:subnet-id "1" :availability-zone "regiona"} {:subnet-id "2" :availability-zone "regionb"}]))

(fact "that populating subnets gives an error when no subnets are found"
      (populate-subnets {:parameters (assoc-in populate-subnets-params [:new-state :tyranitar :deployment-params :selected-zones] ["c"])})
      => (contains {:status :error})
      (provided
       (aws/subnets-by-purpose "environment" "region" "internal") => []))

(fact "that creating an auto scaling group tag works"
      (to-auto-scaling-group-tag "asg" [:Key "value"]) => {:key "Key"
                                                           :value "value"
                                                           :propagate-at-launch true
                                                           :resource-type "auto-scaling-group"
                                                           :resource-id "asg"})

(def create-auto-scaling-group-tags-params
  {:application "application"
   :environment "environment"
   :new-state {:auto-scaling-group-name "new-asg"
               :image-details {:version "new-version"}
               :onix {:contact "new-contact"}}
   :user "user"})

(fact "that creating auto scaling group tags creates the right data"
      (sort (:auto-scaling-group-tags (:new-state (:parameters (create-auto-scaling-group-tags {:parameters create-auto-scaling-group-tags-params})))))
      => ["application-tag" "contact-tag" "deployed-by-tag" "deployed-on-tag" "environment-tag" "name-tag" "version-tag"]
      (provided
       (time/now) => "time"
       (to-auto-scaling-group-tag "new-asg" [:Application "application"]) => "application-tag"
       (to-auto-scaling-group-tag "new-asg" [:Contact "new-contact"]) => "contact-tag"
       (to-auto-scaling-group-tag "new-asg" [:DeployedBy "user"]) => "deployed-by-tag"
       (to-auto-scaling-group-tag "new-asg" [:DeployedOn "time"]) => "deployed-on-tag"
       (to-auto-scaling-group-tag "new-asg" [:Environment "environment"]) => "environment-tag"
       (to-auto-scaling-group-tag "new-asg" [:Name "application-environment-new-version"]) => "name-tag"
       (to-auto-scaling-group-tag "new-asg" [:Version "new-version"]) => "version-tag"))
