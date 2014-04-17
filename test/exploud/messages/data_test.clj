(ns exploud.messages.data-test
  (:require [clj-time.core :as time]
            [exploud
             [aws :as aws]
             [onix :as onix]
             [tyranitar :as tyr]]
            [exploud.messages.data :refer :all]
            [midje.sweet :refer :all]))

(fact "that preparing a deployment moves to the correct task"
      (start-deployment-preparation {:parameters {:application "application" :environment "environment"}})
      => (contains {:status :success
                    :parameters {:application "application"
                                 :environment "environment"
                                 :phase "preparation"}}))

(fact "that validating the region fails when it is nil"
      (validate-region {:parameters {:region nil}}) => (contains {:status :error}))

(fact "that validating the region works when region is present"
      (validate-region {:parameters {:region "region"}}) => (contains {:status :success}))

(fact "that validating the environment fails when it is nil"
      (validate-environment {:parameters {:environment nil}}) => (contains {:status :error}))

(fact "that validating the environment works when environment is present"
      (validate-environment {:parameters {:environment "environment"}}) => (contains {:status :success}))

(fact "that validating the application fails when it is nil"
      (validate-application {:parameters {:application nil}}) => (contains {:status :error}))

(fact "that validating the application works when application is present"
      (validate-application {:parameters {:application "application"}}) => (contains {:status :success}))

(fact "that validating the user fails when it is nil"
      (validate-user {:parameters {:user nil}}) => (contains {:status :error}))

(fact "that validating the user works when user is present"
      (validate-user {:parameters {:user "user"}}) => (contains {:status :success}))

(fact "that validating the image fails when it is nil"
      (validate-image {:parameters {:new-state {:image-details {:id nil}}}}) => (contains {:status :error}))

(fact "that validating the image works when image is present"
      (validate-image {:parameters {:new-state {:image-details {:id "image"}}}}) => (contains {:status :success}))

(fact "that validating the message fails when it is nil"
      (validate-message {:parameters {:message nil}}) => (contains {:status :error}))

(fact "that validating the message works when message is present"
      (validate-message {:parameters {:message "message"}}) => (contains {:status :success}))

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

(fact "that a missing security group by ID is an error"
      (map-security-group-ids {:parameters {:environment "environment"
                                            :region "region"
                                            :new-state {:tyranitar {:deployment-params {:selected-security-groups ["group-1" "sg-group-2-id"]}}}}}) => (contains {:status :error})
      (provided
       (aws/security-groups "environment" "region") => [{:group-id "sg-group-1-id"
                                                         :group-name "group-1"}]))

(fact "that a missing security group by name is an error"
      (map-security-group-ids {:parameters {:environment "environment"
                                            :region "region"
                                            :new-state {:tyranitar {:deployment-params {:selected-security-groups ["group-1" "group-2"]}}}}}) => (contains {:status :error})
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

(fact "populating previous state when a previous one doesn't exist succeeds"
      (populate-previous-state {:parameters {:application "application"
                                             :environment "environment"
                                             :region "region"}})
      => {:parameters {:application "application"
                       :environment "environment"
                       :region "region"}
          :status :success}
      (provided
       (aws/last-application-auto-scaling-group "application" "environment" "region") => nil))

(fact "populating previous tyranitar application properties when no previous state exists succeeds"
      (populate-previous-tyranitar-application-properties {:parameters {:application "application"
                                                                        :environment "environment"}})
      => {:parameters {:application "application"
                       :environment "environment"}
          :status :success})

(fact "getting previous image details when no previous state exists succeeds"
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
