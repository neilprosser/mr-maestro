(ns exploud.actions
  (:require [exploud.asgard :as asg]
            [exploud.onix :as onix]
            [exploud.store :as store]
            [exploud.tyranitar :as tyr]))

(def vpc-id "vpc-7bc88713")

(defn- default-params [region application-name]
  {"azRebalance" "enabled"
   "canaryAssessmentDurationMinutes" 60
   "canaryCapacity" 1
   "canaryStartUpTimeoutMinutes" 30
   "clusterName" application-name
   "defaultCooldown" 10
   "delayDurationMinutes" 0
   "deletePreviousAsg" "Ask"
   "desiredCapacity" 1
   "desiredCapacityAssesmentDurationMinutes" 5
   "desiredCapacityStartUpTimeoutMinutes" 5
   "disablePreviousAsg" "Ask"
   "doCanary" false
   "fullTrafficAssessmentDurationMinutes" 5
   "healthCheckGracePeriod" 600
   "healthCheckType" "EC2"
   "iamInstanceProfile" application-name
   "imageId" ""
   "instanceType" "t1.micro"
   "kernelId" ""
   "keyName" "nprosser-key"
   "max" 1
   "min" 1
   "name" application-name
   "noOptionalDefaults" true
   "notificationDestination" "neil.prosser@nokia.com"
   "pricing" "ON_DEMAND"
   "ramdiskId" ""
   "region" region
   "scaleUp" "Ask"
   (str "selectedLoadBalancersForVpcId" vpc-id) application-name
   "selectedSecurityGroups" []
   "selectedZones" [(str region "a") (str region "b")]
   "subnetPurpose" "internal"
   "terminationPolicy" "Default"
   "ticket" ""})

(defn create-params [region application-name file-params user-params]
  (for [[k v] (seq (merge file-params user-params))
        vs (flatten (conj [] v))]
    [k vs]))

(defn- matches-version? [image version]
  (let [tags (:tags image)]
    (seq (filter (fn [tag] (and (= (:key tag) "version")
                               (= (:value tag) version))) tags))))
(defn image-list-by-version [region application-name version]
  (let [image-list (asg/image-list region application-name)]
    (filter (fn [image] (matches-version? image version)) image-list)))

(defn deploy [region application-name details]
  (if (onix/application-exists? application-name)
    (let [{:keys [ami environment user]} details]
      (if-let [hash (tyr/last-commit-hash environment application-name)]
        (let [configuration {:ami ami
                             :environment environment
                             :hash hash
                             :user user}]
          (if-let [deploy-id (store/store-configuration application-name configuration)]
            (if-let [deployment-params (tyr/deployment-params environment application-name hash)]
              (asg/deploy region application-name (create-params region application-name {"imageId" ami
                                                                                          "ticket" deploy-id}))
              {:status 500 :body "Could not retrieve deployment parameters."})
            {:status 500 :body "Could not store deployment information."}))
        {:status 500 :body "Could not retrieve last commit hash."}))
    {:status 404 :body "Application does not exist."}))

;(deploy "eu-west-1" "skeleton" {:ami "ami-223addf1" :environment "prod" :user "nprosser"})
