(ns maestro.messages.data-test
  (:require [bouncer.core :as b]
            [clj-time.core :as time]
            [maestro
             [alarms :as alarms]
             [aws :as aws]
             [block-devices :as dev]
             [lister :as lister]
             [log :as log]
             [pedantic :as pedantic]
             [policies :as policies]
             [tyrant :as tyr]
             [userdata :as ud]
             [util :as util]
             [validators :as v]]
            [maestro.messages.data :refer :all]
            [midje.sweet :refer :all]
            [ring.util.codec :refer [base64-encode]]))

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

(fact "that getting the Lister metadata fails when there is an error"
      (get-lister-metadata {:parameters {:application "application"}}) => (contains {:status :error})
      (provided
       (lister/application "application") =throws=> (ex-info "Broken" {})))

(fact "that getting the Lister metadata fails when there is no metadata"
      (get-lister-metadata {:parameters {:application "application"}}) => (contains {:status :error})
      (provided
       (lister/application "application") => nil))

(fact "that getting the Lister metadata works"
      (get-lister-metadata {:parameters {:application "application"}}) => (contains {:status :success
                                                                                     :parameters {:application "application"
                                                                                                  :new-state {:onix ..lister..}}})
      (provided
       (lister/application "application") => ..lister..))

(fact "that ensuring deployment is unblocked passes if no block blob detected with Lister"
      (let [pipeline-data {:status     :success
                           :parameters {:application "application"
                                        :new-state   {:onix {}}}}]
        (ensure-unblocked pipeline-data)))

(fact "that ensuring deployment is unblocked causes an error status if block blob detected with Lister"
      (let [pipeline-data {:status     :success
                           :parameters {:application "application"
                                        :new-state   {:onix {:blocked {:user   "myuser"
                                                                       :reason "myreason"}}}}}
            result (ensure-unblocked pipeline-data)]
        result => (contains {:status :error})
        (.getMessage (:throwable result)) => "Application is blocked from deployment by user 'myuser'. Reason: 'myreason'."))

(fact "that ensuring the Tyrant hash doesn't go to Tyrant if the hash is present"
      (ensure-tyrant-hash {:parameters {:application "application"
                                        :environment "environment"
                                        :new-state {:hash "hash"}}}) => {:status :success
                                                                         :parameters {:application "application"
                                                                                      :environment "environment"
                                                                                      :new-state {:hash "hash"}}})

(fact "that ensuring the Tyrant hash goes to Tyrant if the hash isn't present"
      (ensure-tyrant-hash {:parameters {:application "application"
                                        :environment "environment"
                                        :new-state {}}})
      => {:status :success
          :parameters {:application "application"
                       :environment "environment"
                       :new-state {:hash "last-hash"}}}
      (provided
       (tyr/last-commit-hash "environment" "application") => "last-hash"))

(fact "that ensuring the Tyrant hash is an error if there's an exception"
      (ensure-tyrant-hash {:parameters {:application "application"
                                        :environment "environment"
                                        :new-state {}}})
      => (contains {:status :error})
      (provided
       (tyr/last-commit-hash "environment" "application") =throws=> (ex-info "Busted" {})))

(fact "that verifying the Tyrant hash works"
      (verify-tyrant-hash {:parameters {:application "application"
                                        :environment "environment"
                                        :new-state {:hash "hash"}}})
      => (contains {:status :success})
      (provided
       (tyr/verify-commit-hash "environment" "application" "hash") => true))

(fact "that verifying the Tyrant hash is an error if it doesn't match"
      (verify-tyrant-hash {:parameters {:application "application"
                                        :environment "environment"
                                        :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/verify-commit-hash "environment" "application" "hash") => false))

(fact "that an exception while verifying the Tyrant hash is an error"
      (verify-tyrant-hash {:parameters {:application "application"
                                        :environment "environment"
                                        :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/verify-commit-hash "environment" "application" "hash") =throws=> (ex-info "Busted" {})))

(fact "that getting no Tyrant application properties is an error"
      (get-tyrant-application-properties {:parameters {:application "application"
                                                       :environment "environment"
                                                       :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/application-properties "environment" "application" "hash") => nil))

(fact "that an error while getting Tyrant application properties is an error"
      (get-tyrant-application-properties {:parameters {:application "application"
                                                       :environment "environment"
                                                       :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/application-properties "environment" "application" "hash") =throws=> (ex-info "Busted" {})))

(fact "that getting Tyrant application properties works"
      (get-tyrant-application-properties {:parameters {:application "application"
                                                       :environment "environment"
                                                       :new-state {:hash "hash"}}})
      => {:status :success
          :parameters {:application "application"
                       :environment "environment"
                       :new-state {:hash "hash"
                                   :tyranitar {:application-properties {:tyranitar :properties}}}}}
      (provided
       (tyr/application-properties "environment" "application" "hash") => {:tyranitar :properties}))

(fact "that getting no Tyrant application config is a noop"
  (let [parameters {:parameters {:application "application"
                                 :environment "environment"
                                 :new-state {:hash "hash"}}}]
    (get-tyrant-application-config parameters)
    => (contains {:status :success})
    (provided
     (tyr/application-config "environment" "application" "hash") => nil)))

(fact "that an error while getting Tyrant application config is an error"
  (get-tyrant-application-config {:parameters {:application "application"
                                               :environment "environment"
                                               :new-state {:hash "hash"}}})
  => (contains {:status :error})
  (provided
   (tyr/application-config "environment" "application" "hash") =throws=> (ex-info "Busted" {})))

(fact "that getting Tyrant application config works"
      (get-tyrant-application-config {:parameters {:application "application"
                                                   :environment "environment"
                                                   :new-state {:hash "hash"}}})
      => {:status :success
          :parameters {:application "application"
                       :environment "environment"
                       :new-state {:hash "hash"
                                   :tyranitar {:application-config {:tyranitar :config}}}}}
      (provided
       (tyr/application-config "environment" "application" "hash") => {:tyranitar :config}))

(fact "that getting no Tyrant deployment params is an error"
      (get-tyrant-deployment-params {:parameters {:application "application"
                                                  :environment "environment"
                                                  :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/deployment-params "environment" "application" "hash") => nil))

(fact "that an error while getting Tyrant deployment params is an error"
      (get-tyrant-deployment-params {:parameters {:application "application"
                                                  :environment "environment"
                                                  :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/deployment-params "environment" "application" "hash") =throws=> (ex-info "Busted" {})))

(fact "that getting Tyrant deployment params adds in defaults where a value hasn't been specified"
      (get-in (get-tyrant-deployment-params {:parameters {:application "application"
                                                          :environment "environment"
                                                          :new-state {:hash "hash"}}}) [:parameters :new-state :tyranitar :deployment-params])
      => (contains {:default-cooldown 10
                    :desired-capacity 23})
      (provided
       (tyr/deployment-params "environment" "application" "hash") => {:desiredCapacity 23}))

(fact "that generating a validation message does what is required"
      (generate-deployment-params-validation-message {:something '("something has one problem" "something has another problem")
                                                      :other ["other is broken"]})
      => "Validation result:\n* other is broken\n* something has one problem\n* something has another problem")

(fact "that validating Tyrant deployment params is an error if the validation fails"
      (validate-deployment-params {:parameters {:new-state {:tyranitar {:deployment-params {:max "a"}}}}})
      => (contains {:status :error}))

(fact "that getting no Tyrant launch data is an error"
      (get-tyrant-launch-data {:parameters {:application "application"
                                            :environment "environment"
                                            :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/launch-data "environment" "application" "hash") => nil))

(fact "that validating Tyrant deployment params is successful if the validation succeeds"
      (validate-deployment-params {:parameters {:new-state {:tyranitar {:deployment-params {:instance-type "t1.micro"}}}}})
      => (contains {:status :success}))

(fact "that an error while getting Tyrant launch data is an error"
      (get-tyrant-launch-data {:parameters {:application "application"
                                            :environment "environment"
                                            :new-state {:hash "hash"}}})
      => (contains {:status :error})
      (provided
       (tyr/launch-data "environment" "application" "hash") =throws=> (ex-info "Busted" {})))

(fact "that getting Tyrant launch data works"
      (get-tyrant-launch-data {:parameters {:application "application"
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

(fact "that extracting the Tyrant hash from user-data works"
      (extract-hash (base64-encode (.getBytes "export HASH=hash"))) => "hash")

(fact "that getting an error while populating previous state fails the task"
      (def exception (ex-info "Busted" {}))
      (populate-previous-state {:parameters {:application "application"
                                             :environment "environment"
                                             :region "region"}})
      => {:status :error
          :throwable exception}
      (provided
       (aws/last-application-auto-scaling-group "application" "environment" "region") =throws=> exception))

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

(fact "that populating previous state reads the right stuff"
      (let [last-auto-scaling-group {:auto-scaling-group-name ..auto-scaling-group-name..
                                     :availability-zones ..availability-zones..
                                     :default-cooldown ..default-cooldown..
                                     :desired-capacity 6
                                     :health-check-grace-period ..health-check-grace-period..
                                     :health-check-type ..health-check-type..
                                     :load-balancer-names ..load-balancer-names..
                                     :max-size 6
                                     :min-size 6
                                     :tags ..tags..
                                     :termination-policies ..termination-policies..
                                     :vpczone-identifier ..vpczone-identifier..
                                     :launch-configuration-name ..launch-configuration-name..}
            launch-configuration {:image-id ..image-id..
                                  :launch-configuration-name ..launch-configuration-name..
                                  :security-groups ..security-groups..
                                  :user-data (base64-encode (.getBytes "user-data"))}
            deployment-params {:desired-capacity 3
                               :load-balancer-healthy-attempts 34
                               :max 3
                               :min 3
                               :random-property true
                               :selected-load-balancers "lb"}]
        (populate-previous-state {:parameters {:application "application"
                                               :environment "environment"
                                               :region "region"}})
        => {:parameters {:application "application"
                         :environment "environment"
                         :previous-state {:auto-scaling-group-name ..auto-scaling-group-name..
                                          :auto-scaling-group-tags ..tags..
                                          :availability-zones ..availability-zones..
                                          :hash ..hash..
                                          :image-details {:id ..image-id..}
                                          :launch-configuration-name ..launch-configuration-name..
                                          :selected-security-group-ids ..security-groups..
                                          :termination-policies ..termination-policies..
                                          :tyranitar {:deployment-params {:default-cooldown ..default-cooldown..
                                                                          :desired-capacity 6
                                                                          :ebs-optimized false
                                                                          :health-check-grace-period ..health-check-grace-period..
                                                                          :health-check-type ..health-check-type..
                                                                          :instance-healthy-attempts 100
                                                                          :load-balancer-healthy-attempts 34
                                                                          :max 6
                                                                          :min 6
                                                                          :pause-after-instances-healthy false
                                                                          :pause-after-load-balancers-healthy false
                                                                          :random-property true
                                                                          :selected-load-balancers ..load-balancer-names..
                                                                          :subnet-purpose "internal"
                                                                          :termination-policy "Default"}}
                                          :user-data "user-data"
                                          :vpc-zone-identifier ..vpczone-identifier..}
                         :region "region"}
            :status :success}
        (provided
         (aws/last-application-auto-scaling-group "application" "environment" "region") => last-auto-scaling-group
         (aws/launch-configuration ..launch-configuration-name.. "environment" "region") => launch-configuration
         (extract-hash anything) => ..hash..
         (tyr/deployment-params "environment" "application" ..hash..) => deployment-params)))

(fact "that getting an error while populating previous Tyrant application properties fails the task"
      (def exception (ex-info "Busted" {}))
      (populate-previous-tyrant-application-properties {:parameters {:application "application"
                                                                     :environment "environment"
                                                                     :previous-state {:hash "hash"}}})
      => {:status :error
          :throwable exception}
      (provided
       (tyr/application-properties "environment" "application" "hash") =throws=> exception))

(fact "that populating previous Tyrant application properties when no previous state exists succeeds"
      (populate-previous-tyrant-application-properties {:parameters {:application "application"
                                                                     :environment "environment"}})
      => {:parameters {:application "application"
                       :environment "environment"}
          :status :success})

(fact "that populating previous Tyrant application properties populates the right keys"
      (populate-previous-tyrant-application-properties {:parameters {:application "application"
                                                                     :environment "environment"
                                                                     :previous-state {:hash "previous-hash"}}})
      => {:parameters {:application "application"
                       :environment "environment"
                       :previous-state {:hash "previous-hash"
                                        :tyranitar {:application-properties {:healthcheck.path "healthcheck.path"
                                                                             :service.healthcheck.path "service.healthcheck.path"
                                                                             :service.healthcheck.skip "service.healthcheck.skip"
                                                                             :service.port "service.port"}}}}
          :status :success}
      (provided
       (tyr/application-properties "environment" "application" "previous-hash")
       => {:healthcheck.path "healthcheck.path"
           :service.healthcheck.path "service.healthcheck.path"
           :service.healthcheck.skip "service.healthcheck.skip"
           :service.port "service.port"
           :something-else "something-else"}))

(fact "that getting previous image details when no previous state exists succeeds"
      (get-previous-image-details {:parameters {:environment "environment"
                                                :region "region"}})
      => {:parameters {:environment "environment"
                       :region "region"}
          :status :success})

(fact "that getting an error while getting previous image details fails the task"
      (def exception (ex-info "Busted" {}))
      (get-previous-image-details {:parameters {:environment "environment"
                                                :previous-state {:image-details {:id "image-id"}}
                                                :region "region"}})
      => {:status :error
          :throwable exception}
      (provided
       (aws/image "image-id" "environment" "region") =throws=> exception))

(fact "that getting previous image details works"
      (get-previous-image-details {:parameters {:environment "environment"
                                                :previous-state {:image-details {:id "image-id"}}
                                                :region "region"}})
      => {:parameters {:environment "environment"
                       :previous-state {:image-details {:id "image-id"
                                                        :details "details"}}
                       :region "region"}
          :status :success}
      (provided
       (aws/image "image-id" "environment" "region") => {:name "image-name"}
       (util/image-details "image-name") => {:details "details"}))

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
       (aws/image "image-id" "environment" "region") => ..image..
       (util/image-details ..image..) => {:details "details"}))

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

(fact "that a matching image application and application is allowed when verifying an image"
      (verify-image {:parameters {:application "application"
                                  :new-state {:image-details {:application "application"
                                                              :image-name "image-name"}}}})
      => {:parameters {:application "application"
                       :new-state {:image-details {:application "application"
                                                   :image-name "image-name"}}}
          :status :success})

(fact "that a non-matching image application and application isn't allowed when verifying an image"
      (verify-image {:parameters {:application "application"
                                  :new-state {:image-details {:application "different"
                                                              :image-name "image-name"}}}})
      => (contains {:status :error}))

(fact "that checking for an embargo is successful no embargo tag is present"
      (check-for-embargo {:parameters {:environment "environment"
                                       :new-state {:image-details {:tags {}}}}})
      => (contains {:status :success}))

(fact "that checking for an embargo is successful if the environment is not embargoed"
      (check-for-embargo {:parameters {:environment "environment"
                                       :new-state {:image-details {:tags {:embargo "anotherenv"}}}}})
      => (contains {:status :success}))

(fact "that checking for an embargo is successful no embargo tag is present"
      (check-for-embargo {:parameters {:environment "environment"
                                       :new-state {:image-details {:tags {:embargo "environment"}}}}})
      => (contains {:status :error}))

(fact "that checking whether the image and instance type are compatible results in success when they are"
      (check-instance-type-compatibility {:parameters {:new-state {:image-details {:virt-type "hvm"}
                                                                   :tyranitar {:deployment-params {:instance-type "i2.8xlarge"}}}}})
      => (contains {:status :success}))

(fact "that checking whether the image and instance type are compatible results in an error when they aren't"
      (check-instance-type-compatibility {:parameters {:new-state {:image-details {:virt-type "para"}
                                                                   :tyranitar {:deployment-params {:instance-type "t2.micro"}}}}})
      => (contains {:status :error}))

(fact "that an application with no contact property set errors the task"
      (check-contact-property {:parameters {:application "application"
                                            :new-state {:onix {}}}})
      => (contains {:status :error}))

(fact "that an application with a contact property set is allowed"
      (check-contact-property {:parameters {:application "application"
                                            :new-state {:onix {:contact "hello"}}}})
      => (contains {:status :success}))

(fact "that checking Pedantic configuration succeeds when Pedantic configuration exists"
      (check-pedantic-configuration {:parameters {:application "application"
                                                  :environment "poke"}})
      => (contains {:status :success})
      (provided
       (pedantic/configuration "poke" "application") => {}))

(fact "that checking Pedantic configuration fails when Pedantic configuration doesn't exist"
      (check-pedantic-configuration {:parameters {:application "application"
                                                  :environment "poke"}})
      => (contains {:status :error})
      (provided
       (pedantic/configuration "poke" "application") => nil))

(fact "that checking Pedantic configuration retries when Pedantic throws up"
      (check-pedantic-configuration {:parameters {:application "application"
                                                  :environment "prod"}})
      => (contains {:status :retry})
      (provided
       (pedantic/configuration "prod" "application") =throws=> (ex-info "Busted" {:type :maestro.pedantic/unexpected-response})))

(fact "that checking Pedantic configuration ignores environments other than 'poke' or 'prod'"
      (check-pedantic-configuration {:parameters {:application "application"
                                                  :environment "environment"}})
      => (contains {:status :success})
      (provided
       (pedantic/configuration "environment" "application") => {} :times 0))

(fact "that adding the block device mappings works if nothing has been provided"
      (create-block-device-mappings {:parameters {:new-state {:image-details {:virt-type "para"}
                                                              :tyranitar {:deployment-params {:instance-type "c3.2xlarge"}}}}})
      => {:parameters {:new-state {:block-device-mappings ..block-devices..
                                   :image-details {:virt-type "para"}
                                   :tyranitar {:deployment-params {:instance-type "c3.2xlarge"}}}}
          :status :success}
      (provided
       (dev/create-mappings nil 2 nil "para") => ..block-devices..))

(fact "that adding the block device mappings work if something has been provided"
      (create-block-device-mappings {:parameters {:new-state {:image-details {:virt-type "hvm"}
                                                              :tyranitar {:deployment-params {:instance-type "m1.xlarge"
                                                                                              :volumes {:root {:size 12}
                                                                                                        :instance-stores 2
                                                                                                        :block-devices [{:size 20
                                                                                                                         :type "gp2"}]}}}}}})
      => {:parameters {:new-state {:block-device-mappings ..block-devices..
                                   :image-details {:virt-type "hvm"}
                                   :tyranitar {:deployment-params {:instance-type "m1.xlarge"
                                                                   :volumes {:root {:size 12}
                                                                             :instance-stores 2
                                                                             :block-devices [{:size 20
                                                                                              :type "gp2"}]}}}}}
          :status :success}
      (provided
       (dev/create-mappings {:size 12} 2 [{:size 20 :type "gp2"}] "hvm") => ..block-devices..))

(fact "that adding required security groups includes them in the parameters when there are no security groups present"
      (add-required-security-groups {:parameters {:new-state {:tyranitar {:deployment-params {:selected-security-groups nil}}}}})
      => {:parameters {:new-state {:tyranitar {:deployment-params {:selected-security-groups ["maestro-healthcheck"]}}}}
          :status :success})

(fact "that adding required security groups includes them in the parameters when there are already security groups present"
      (add-required-security-groups {:parameters {:new-state {:tyranitar {:deployment-params {:selected-security-groups ["security-group"]}}}}})
      => {:parameters {:new-state {:tyranitar {:deployment-params {:selected-security-groups ["security-group" "maestro-healthcheck"]}}}} :status :success})

(fact "that verifying a deployment with no load balancers does nothing"
      (verify-load-balancers {:parameters {:environment "environment"
                                           :new-state {:tyranitar {:deployment-params {:selected-load-balancers nil}}}
                                           :region "region"}})
      => (contains {:status :success})
      (provided
       (aws/load-balancers-with-names anything anything anything) => nil :times 0))

(fact "that verifying a deployment with empty load balancers does nothing"
      (verify-load-balancers {:parameters {:environment "environment"
                                           :new-state {:tyranitar {:deployment-params {:selected-load-balancers []}}}
                                           :region "region"}})
      => (contains {:status :success})
      (provided
       (aws/load-balancers-with-names anything anything anything) => nil :times 0))

(fact "that an error while verifying load balancers fails the task"
      (verify-load-balancers {:parameters {:environment "environment"
                                           :new-state {:tyranitar {:deployment-params {:selected-load-balancers ["elb"]}}}
                                           :region "region"}})
      => (contains {:status :error})
      (provided
       (aws/load-balancers-with-names "environment" "region" ["elb"]) =throws=> (ex-info "Busted" {})))

(fact "that not finding all listed load balancers fails the task"
      (verify-load-balancers {:parameters {:environment "environment"
                                           :new-state {:tyranitar {:deployment-params {:selected-load-balancers ["elb1" "elb2"]}}}
                                           :region "region"}})
      => (contains {:status :error})
      (provided
       (aws/load-balancers-with-names "environment" "region" ["elb1" "elb2"]) => {"elb1" {} "elb2" nil}))

(fact "that finding all listed load balancers means a successful task"
      (verify-load-balancers {:parameters {:environment "environment"
                                           :new-state {:tyranitar {:deployment-params {:selected-load-balancers ["elb1" "elb2"]}}}
                                           :region "region"}})
      => (contains {:status :success})
      (provided
       (aws/load-balancers-with-names "environment" "region" ["elb1" "elb2"]) => {"elb1" {} "elb2" {}}))

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

(fact "that checking for deleted load balancers is successful if there were no previous load balancers"
      (check-for-deleted-load-balancers {:parameters {:previous-state {:tyranitar {:deployment-params {:selected-load-balancers nil}}}}})
      => (contains {:status :success})
      (provided
       (aws/load-balancers-with-names anything anything anything) => nil :times 0))

(fact "that checking for deleted load balancers is successful if there was an empty list of previous load balancers"
      (check-for-deleted-load-balancers {:parameters {:previous-state {:tyranitar {:deployment-params {:selected-load-balancers []}}}}})
      => (contains {:status :success})
      (provided
       (aws/load-balancers-with-names anything anything anything) => nil :times 0))

(def populate-subnets-params
  {:environment "environment"
   :new-state {:tyranitar {:deployment-params {:subnet-purpose "internal"}}}
   :region "region"})

(fact "that populating subnets uses all subnets found when `selected-zones` has not been given"
      (:new-state (:parameters (populate-subnets {:parameters populate-subnets-params})))
      => (contains {:availability-zones #{"regiona" "regionb"}
                    :selected-subnets ["1" "2"]})
      (provided
       (aws/subnets-by-purpose "environment" "region" "internal") => [{:subnet-id "1" :availability-zone "regiona"} {:subnet-id "2" :availability-zone "regionb"}]))

(fact "that populating subnets correctly filters the subnets based on the contents of `availability-zones` when `selected-zones` has been given"
      (:new-state (:parameters (populate-subnets {:parameters (assoc-in populate-subnets-params [:new-state :tyranitar :deployment-params :selected-zones] ["b"])})))
      => (contains {:availability-zones #{"regionb"}
                    :selected-subnets ["2"]})
      (provided
       (aws/subnets-by-purpose "environment" "region" "internal") => [{:subnet-id "1" :availability-zone "regiona"} {:subnet-id "2" :availability-zone "regionb"}]))

(fact "that populating subnets dedupes the availability zones"
      (:new-state (:parameters (populate-subnets {:parameters (assoc-in populate-subnets-params [:new-state :tyranitar :deployment-params :selected-zones] ["b"])})))
      => (contains {:availability-zones #{"regionb"}
                    :selected-subnets ["1" "2"]})
      (provided
       (aws/subnets-by-purpose "environment" "region" "internal") => [{:subnet-id "1" :availability-zone "regionb"} {:subnet-id "2" :availability-zone "regionb"}]))

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

(fact "that populating the VPC zone identifier joins the subnets"
      (populate-vpc-zone-identifier {:parameters {:new-state {:selected-subnets ["subnet-1" "subnet-2"]}}})
      => {:parameters {:new-state {:selected-subnets ["subnet-1" "subnet-2"]
                                   :vpc-zone-identifier "subnet-1,subnet-2"}}
          :status :success})

(fact "that populating the termination policies does the right thing"
      (populate-termination-policies {:parameters {:new-state {:tyranitar {:deployment-params {:termination-policy "Oldest"}}}}})
      => {:parameters {:new-state {:termination-policies ["Oldest"]
                                   :tyranitar {:deployment-params {:termination-policy "Oldest"}}}}
          :status :success})

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

(fact "that generating user-data adds it to the parameters"
      (generate-user-data {:parameters {:some "parameters"}})
      => {:parameters {:new-state {:user-data ..user-data..}
                       :some "parameters"}
          :status :success}
      (provided
       (ud/create-user-data {:some "parameters"}) => ..user-data..))

(fact "that populating previous scaling policies works"
      (populate-previous-scaling-policies {:parameters {:environment "environment"
                                                        :previous-state {:auto-scaling-group-name "asg"}
                                                        :region "region"}})
      => {:status :success
          :parameters {:environment "environment"
                       :previous-state {:auto-scaling-group-name "asg"
                                        :scaling-policies [{:policy-name "policy-1"}
                                                           {:policy-name "policy-2"}]}
                       :region "region"}}
      (provided
       (policies/policies-for-auto-scaling-group "environment" "region" "asg") => [{:policy-name "policy-1"} {:policy-name "policy-2"}]))

(fact "that an error while populating previous scaling policies is handled"
      (populate-previous-scaling-policies {:parameters {:environment "environment"
                                                        :previous-state {:auto-scaling-group-name "asg"}
                                                        :region "region"}})
      => (contains {:status :error})
      (provided
       (policies/policies-for-auto-scaling-group "environment" "region" "asg") =throws=> (ex-info "Busted" {})))

(fact "that populating previous scaling policies does nothing if there's no previous state"
      (populate-previous-scaling-policies {:parameters {:environment "environment"
                                                        :region "region"}})
      => (contains {:status :success})
      (provided
       (policies/policies-for-auto-scaling-group "environment" "region" anything) => nil :times 0))

(fact "that generating scaling policies work"
      (generate-scaling-policies {:parameters {:new-state {:auto-scaling-group-name "asg"
                                                           :tyranitar {:deployment-params {:policies [{:policy-name "policy-1"}
                                                                                                      {:policy-name "policy-2"}]}}}}})
      => {:status :success
          :parameters {:new-state {:auto-scaling-group-name "asg"
                                   :scaling-policies [{:auto-scaling-group-name "asg"
                                                       :policy-name "policy-1"}
                                                      {:auto-scaling-group-name "asg"
                                                       :policy-name "policy-2"}]
                                   :tyranitar {:deployment-params {:policies [{:policy-name "policy-1"}
                                                                              {:policy-name "policy-2"}]}}}}})

(fact "that filtering CloudWatch alarms turns them into something we can use as a parameter"
      (filter-alarm {:excluded true :actions-enabled true :okactions ["action"] :unit nil}) => {:actions-enabled true :ok-actions ["action"]})

(fact "that getting an error while populating previous CloudWatch alarms fails the task"
      (populate-previous-cloudwatch-alarms {:parameters {:environment ..environment..
                                                         :previous-state {:auto-scaling-group-name ..previous-asg..
                                                                          :previous "state"}
                                                         :region ..region..}})
      => (contains {:status :error})
      (provided
       (alarms/alarms-for-auto-scaling-group ..environment.. ..region.. ..previous-asg..) =throws=> (ex-info "Busted" {})))

(fact "that populating previous CloudWatch alarms does nothing if there's no previous state"
      (populate-previous-cloudwatch-alarms {:parameters {:environment ..environment..
                                                         :previous-state nil
                                                         :region ..region..}})
      => (contains {:status :success})
      (provided
       (alarms/alarms-for-auto-scaling-group anything anything anything) => nil :times 0))

(fact "that populating previous CloudWatch alarms does what we need"
      (populate-previous-cloudwatch-alarms {:parameters {:environment ..environment..
                                                         :previous-state {:auto-scaling-group-name ..previous-asg..
                                                                          :previous "state"}
                                                         :region ..region..}})
      => {:status :success
          :parameters {:environment ..environment..
                       :previous-state {:auto-scaling-group-name ..previous-asg..
                                        :cloudwatch-alarms [..alarm-1.. ..alarm-2..]
                                        :previous "state"}
                       :region ..region..}}
      (provided
       (alarms/alarms-for-auto-scaling-group ..environment.. ..region.. ..previous-asg..) => [..full-alarm-1.. ..full-alarm-2..]
       (filter-alarm ..full-alarm-1..) => ..alarm-1..
       (filter-alarm ..full-alarm-2..) => ..alarm-2..))

(fact "that generating CloudWatch alarms populates the new-state with all alarms"
      (generate-cloudwatch-alarms {:parameters {:environment ..environment..
                                                :new-state {:auto-scaling-group-name "asg"
                                                            :tyranitar {:deployment-params {:alarms [..alarm-1.. ..alarm-2..]}}}
                                                :region ..region..}})
      => {:status :success
          :parameters {:environment ..environment..
                       :new-state {:auto-scaling-group-name "asg"
                                   :cloudwatch-alarms [..standard-alarm-1..
                                                       ..standard-alarm-2..
                                                       {:alarm-name "asg-alarm-1" :dimensions [{:name "AutoScalingGroupName" :value "asg"}]}
                                                       {:alarm-name "asg-alarm-2" :dimensions [{:name "AutoScalingGroupName" :value "asg"}]}]
                                   :tyranitar {:deployment-params {:alarms [..alarm-1.. ..alarm-2..]}}}
                       :region ..region..}}
      (provided
       (alarms/standard-alarms ..environment.. ..region.. {:auto-scaling-group-name "asg"
                                                           :tyranitar {:deployment-params {:alarms [..alarm-1.. ..alarm-2..]}}}) => [..standard-alarm-1.. ..standard-alarm-2..]
       (filter-alarm ..alarm-1..) => {:alarm-name "alarm-1"}
       (filter-alarm ..alarm-2..) => {:alarm-name "alarm-2"}))

(fact "that validating a CloudWatch alarm combines the messages correctly"
      (apply hash-set (validate-cloudwatch-alarm {:statistic "broken"
                                                  :unit "broken"})) => #{"unit must be a valid unit" "statistic must be a valid statistic"})

(fact "that validating CloudWatch alarms generates the right message"
      (validate-cloudwatch-alarms {:parameters {:new-state {:cloudwatch-alarms [{:alarm-name "alarm-1"} {:alarm-name "alarm-2"}]}}}) => (contains {:status :error})
      (provided
       (validate-cloudwatch-alarm {:alarm-name "alarm-1"}) => nil
       (validate-cloudwatch-alarm {:alarm-name "alarm-2"}) => ["Message 1", "Message 2"]
       (log/write "Validating CloudWatch alarms.") => nil
       (log/write "Validation result:\n* alarm-2 - [\"Message 1\" \"Message 2\"]") => nil))

(fact "that validating CloudWatch alarms generates no message when everything is good"
      (validate-cloudwatch-alarms {:parameters {:new-state {:cloudwatch-alarms [{:alarm-name "alarm-1"} {:alarm-name "alarm-2"}]}}}) => (contains {:status :success})
      (provided
       (validate-cloudwatch-alarm {:alarm-name "alarm-1"}) => nil
       (validate-cloudwatch-alarm {:alarm-name "alarm-2"}) => nil
       (log/write "Validating CloudWatch alarms.") => nil))

(fact "that ensuring known policies fails if any policies are not present"
      (ensure-known-policies {:parameters {:new-state {:cloudwatch-alarms [{:alarm-actions ["arn1" {:policy "policy-1"}]}]}}})
      => (contains {:status :error}))

(fact "that ensuring known policies succeeds if all policies are present"
      (ensure-known-policies {:parameters {:new-state {:cloudwatch-alarms [{:alarm-actions ["arn1" {:policy "policy-1"}]}]
                                                       :scaling-policies [{:policy-name "policy-1"}]}}})
      => (contains {:status :success}))

(fact "that completing deployment preparation works"
      (complete-deployment-preparation {:parameters {:application "application"
                                                     :environment "environment"}})
      => (contains {:status :success}))

(fact "that starting the deployment adds the required information to the parameters and removes any previous `:end` key"
      (start-deployment {:parameters {:application "application"
                                      :end "whatever"
                                      :environment "environment"}})
      => {:parameters {:application "application"
                       :environment "environment"
                       :phase "deployment"
                       :status "running"}
          :status :success}
      (provided
       (log/write "Starting deployment of 'application' to 'environment'.") => nil))

(fact "that starting the deployment (when it is an undo) adds the required information to the parameters and removes any previous `:end` key"
      (start-deployment {:parameters {:application "application"
                                      :end "whatever"
                                      :environment "environment"
                                      :undo true}})
      => {:parameters {:application "application"
                       :environment "environment"
                       :phase "deployment"
                       :status "running"
                       :undo true}
          :status :success}
      (provided
       (log/write "Starting undo of 'application' in 'environment'.") => nil))

(fact "that completing the deployment adds an end key to our parameters"
      (complete-deployment {:parameters {:application "application"
                                         :environment "environment"}})
      => {:status :success
          :parameters {:application "application"
                       :end ..now..
                       :environment "environment"}}
      (provided
       (time/now) => ..now..))

(fact "that completing a deployment writes the correct message"
      (complete-deployment {:parameters {:application "application"
                                         :environment "environment"}})
      => {:status :success
          :parameters {:application "application"
                       :end ..now..
                       :environment "environment"}}
      (provided
       (log/write "Deployment of 'application' to 'environment' complete.") => ..log..
       (time/now) => ..now..))

(fact "that completing an undone deployment writes the correct message"
      (complete-deployment {:parameters {:application "application"
                                         :environment "environment"
                                         :undo true}})
      => {:status :success
          :parameters {:application "application"
                       :end ..now..
                       :environment "environment"
                       :undo true}}
      (provided
       (log/write "Undo of 'application' in 'environment' complete.") => ..log..
       (time/now) => ..now..))
