(ns maestro.messages.data
  (:require [bouncer.core :as b]
            [clj-time.core :as time]
            [clojure
             [set :as set]
             [string :as str]]
            [environ.core :refer [env]]
            [maestro
             [aws :as aws]
             [block-devices :as dev]
             [hubot :as hubot]
             [lister :as lister]
             [log :as log]
             [naming :as naming]
             [pedantic :as pedantic]
             [responses :refer :all]
             [tyrant :as tyr]
             [userdata :as ud]
             [util :as util]
             [validators :as v]]
            [ring.util.codec :refer [base64-decode]]))

(def ^:private required-security-group-names
  (str/split (env :aws-required-security-groups) #","))

(def ^:private instance-info
  {"c1.medium" {:instance-stores 1}
   "c1.xlarge" {:instance-stores 4}
   "c3.large" {:instance-stores 2}
   "c3.xlarge" {:instance-stores 2}
   "c3.2xlarge" {:instance-stores 2}
   "c3.4xlarge" {:instance-stores 2}
   "c3.8xlarge" {:instance-stores 2}
   "cc2.8xlarge" {:instance-stores 4}
   "cg1.4xlarge" {:instance-stores 2}
   "cr1.8xlarge" {:instance-stores 2}
   "g2.2xlarge" {:instance-stores 1}
   "hi1.4xlarge" {:instance-stores 2}
   "hs1.8xlarge" {:instance-stores 24}
   "i2.xlarge" {:instance-stores 1}
   "i2.2xlarge" {:instance-stores 2}
   "i2.4xlarge" {:instance-stores 4}
   "i2.8xlarge" {:instance-stores 8}
   "m1.small" {:instance-stores 1}
   "m1.medium" {:instance-stores 1}
   "m1.large" {:instance-stores 2}
   "m1.xlarge" {:instance-stores 4}
   "m2.xlarge" {:instance-stores 1}
   "m2.2xlarge" {:instance-stores 1}
   "m2.4xlarge" {:instance-stores 2}
   "m3.medium" {:instance-stores 1}
   "m3.large" {:instance-stores 1}
   "m3.xlarge" {:instance-stores 2}
   "m3.2xlarge" {:instance-stores 2}
   "r3.large" {:instance-stores 1}
   "r3.xlarge" {:instance-stores 1}
   "r3.2xlarge" {:instance-stores 1}
   "r3.4xlarge" {:instance-stores 1}
   "r3.8xlarge" {:instance-stores 2}
   "t1.micro" {:instance-stores 0}
   "t2.small" {:instance-stores 0}
   "t2.micro" {:instance-stores 0}
   "t2.medium" {:instance-stores 0}})

(defn start-deployment-preparation
  [{:keys [parameters]}]
  (let [{:keys [application environment]} parameters]
    (log/write (format "Preparing deployment of '%s' to '%s'." application environment))
    (success (assoc parameters :phase "preparation"))))

(defn validate-deployment
  [{:keys [parameters]}]
  (log/write "Validating deployment.")
  (let [result (b/validate parameters v/deployment-validators)]
    (if-let [details (first result)]
      (error-with (ex-info "Deployment validation failed." {:type ::deployment-validation-failed
                                                            :details details}))
      (success parameters))))

(defn get-lister-metadata
  [{:keys [parameters]}]
  (log/write "Getting Lister metadata.")
  (when-let [application (:application parameters)]
    (try
      (if-let [lister-details (lister/application application)]
        (success (assoc-in parameters [:new-state :onix] lister-details))
        (error-with (ex-info "Failed to obtain Lister metadata." {:application application})))
      (catch Exception e
        (error-with e)))))

(defn ensure-tyrant-hash
  [{:keys [parameters]}]
  (let [{:keys [application environment]} parameters
        state (:new-state parameters)
        {:keys [hash]} state]
    (try
      (if hash
        (log/write "Using given Tyrant hash.")
        (log/write "Getting latest Tyrant hash."))
      (success (assoc-in parameters [:new-state :hash] (or hash (tyr/last-commit-hash environment application))))
      (catch Exception e
        (error-with e)))))

(defn verify-tyrant-hash
  [{:keys [parameters]}]
  (let [{:keys [application environment]} parameters
        state (:new-state parameters)
        {:keys [hash]} state]
    (try
      (log/write (format "Verifying Tyrant hash '%s'." hash))
      (if (tyr/verify-commit-hash environment application hash)
        (success parameters)
        (do
          (log/write (format "Hash '%s' does not exist for application '%s' in environment '%s'." hash application environment))
          (error-with (ex-info "Hash does not exist for application and environment" {:type ::hash-verification-failed
                                                                                      :application application
                                                                                      :environment environment
                                                                                      :hash hash}))))
      (catch Exception e
        (error-with e)))))

(defn get-tyrant-application-properties
  [{:keys [parameters]}]
  (let [{:keys [application environment]} parameters
        state (:new-state parameters)
        {:keys [hash]} state]
    (try
      (log/write "Retrieving application-properties.")
      (if-let [application-properties (tyr/application-properties environment application hash)]
        (success (assoc-in parameters [:new-state :tyranitar :application-properties] application-properties))
        (error-with (ex-info "No application properties found." {:type ::no-application-properties
                                                                 :application application
                                                                 :environment environment})))
      (catch Exception e
        (error-with e)))))

(def default-deployment-params
  {:default-cooldown 10
   :desired-capacity 1
   :health-check-grace-period 600
   :health-check-type "EC2"
   :instance-healthy-attempts 50
   :instance-type "t1.micro"
   :load-balancer-healthy-attempts 50
   :max 1
   :min 1
   :pause-after-instances-healthy false
   :pause-after-load-balancers-healthy false
   :subnet-purpose "internal"
   :termination-policy "Default"})

(defn get-tyrant-deployment-params
  [{:keys [parameters]}]
  (let [{:keys [application environment]} parameters
        state (:new-state parameters)
        {:keys [hash]} state]
    (try
      (log/write "Retrieving deployment-params.")
      (if-let [actual-deployment-params (util/clojurize-keys (tyr/deployment-params environment application hash))]
        (success (assoc-in parameters [:new-state :tyranitar :deployment-params] (merge default-deployment-params
                                                                                        (update-in actual-deployment-params [:selected-load-balancers] (comp seq util/list-from)))))
        (error-with (ex-info "No deployment params found." {:type ::no-deployment-params
                                                            :application application
                                                            :environment environment})))
      (catch Exception e
        (error-with e)))))

(defn generate-validation-message
  [result]
  (str/trim (str "Validation result:\n"
                 (with-out-str
                   (doseq [[key value] result
                           message value]
                     (println (str "* " message)))))))

(defn validate-deployment-params
  [{:keys [parameters]}]
  (let [state (:new-state parameters)
        {:keys [tyranitar]} state
        {:keys [deployment-params]} tyranitar
        validation-result (b/validate deployment-params v/deployment-params-validators)]
    (log/write "Validating deployment params.")
    (if-let [details (first validation-result)]
      (do
        (log/write (generate-validation-message details))
        (error-with (ex-info "Deployment params are invalid." {:type ::invalid-deployment-params})))
      (success parameters))))

(defn get-tyrant-launch-data
  [{:keys [parameters]}]
  (let [{:keys [application environment]} parameters
        state (:new-state parameters)
        {:keys [hash]} state]
    (try
      (log/write "Retrieving launch-data.")
      (if-let [launch-data (tyr/launch-data environment application hash)]
        (success (assoc-in parameters [:new-state :tyranitar :launch-data] launch-data))
        (error-with (ex-info "No launch data found." {:type ::no-launch-data
                                                      :application application
                                                      :environment environment})))
      (catch Exception e
        (error-with e)))))

(defn- extract-hash
  [user-data]
  (when user-data
    (second (re-find #"export HASH=([^\s]+)" (String. (base64-decode user-data))))))

(defn- assoc-auto-scaling-group-properties
  [state {:keys [auto-scaling-group-name availability-zones default-cooldown desired-capacity health-check-grace-period health-check-type load-balancer-names max-size min-size tags termination-policies vpczone-identifier] :as auto-scaling-group}]
  (if auto-scaling-group
    (-> state
        (assoc :auto-scaling-group-name auto-scaling-group-name
               :auto-scaling-group-tags tags
               :availability-zones availability-zones
               :termination-policies termination-policies
               :vpc-zone-identifier vpczone-identifier)
        (assoc-in [:tyranitar :deployment-params :default-cooldown] default-cooldown)
        (assoc-in [:tyranitar :deployment-params :desired-capacity] desired-capacity)
        (assoc-in [:tyranitar :deployment-params :health-check-grace-period] health-check-grace-period)
        (assoc-in [:tyranitar :deployment-params :health-check-type] health-check-type)
        (assoc-in [:tyranitar :deployment-params :max] max-size)
        (assoc-in [:tyranitar :deployment-params :min] min-size)
        (assoc-in [:tyranitar :deployment-params :selected-load-balancers] load-balancer-names))
    state))

(defn- assoc-launch-configuration-properties
  [state {:keys [image-id instance-type launch-configuration-name security-groups user-data] :as launch-configuration}]
  (if launch-configuration
    (-> state
        (assoc :launch-configuration-name launch-configuration-name
               :hash (extract-hash user-data)
               :selected-security-group-ids security-groups
               :user-data (String. (base64-decode user-data)))
        (assoc-in [:tyranitar :deployment-params :instance-type] instance-type)
        (assoc-in [:image-details :id] image-id))
    state))

(defn- assoc-previous-state
  [{:keys [application environment region] :as parameters} last-auto-scaling-group]
  (if last-auto-scaling-group
    (assoc parameters :previous-state (-> (:previous-status parameters)
                                          (assoc-auto-scaling-group-properties last-auto-scaling-group)
                                          (assoc-launch-configuration-properties (aws/launch-configuration (:launch-configuration-name last-auto-scaling-group) environment region))))
    parameters))

(defn populate-previous-state
  [{:keys [parameters]}]
  (let [{:keys [application environment region]} parameters]
    (try
      (if-let [last-auto-scaling-group (aws/last-application-auto-scaling-group application environment region)]
        (do
          (log/write "Populating previous state.")
          (success (assoc-previous-state parameters last-auto-scaling-group)))
        (success parameters))
      (catch Exception e
        (error-with e)))))

(defn populate-previous-tyrant-application-properties
  [{:keys [parameters]}]
  (let [{:keys [application environment]} parameters
        state (:previous-state parameters)
        {:keys [hash]} state]
    (if state
      (try
        (log/write "Populating previous application-properties.")
        (success (assoc-in parameters [:previous-state :tyranitar :application-properties] (select-keys (tyr/application-properties environment application hash) [:service.healthcheck.path :service.healthcheck.skip :service.port])))
        (catch Exception e
          (error-with e)))
      (success parameters))))

(defn get-previous-image-details
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state (:previous-state parameters)
        image (get-in state [:image-details :id])]
    (if state
      (try
        (log/write (format "Retrieving previous image details for '%s'." image))
        (let [{:keys [name]} (aws/image image environment region)]
          (success (update-in parameters [:previous-state :image-details] merge (util/image-details name))))
        (catch Exception e
          (error-with e)))
      (success parameters))))

(defn create-names
  [{:keys [parameters]}]
  (let [{:keys [application environment previous-state]} parameters
        next-asg-info (naming/next-asg-info (:auto-scaling-group-name previous-state (naming/create-asg-name {:application application :environment environment :iteration 0})))
        launch-configuration-name (naming/create-launch-config-name next-asg-info)
        auto-scaling-group-name (naming/create-asg-name next-asg-info)]
    (log/write (format "New launch configuration will be called '%s' and new auto scaling group will be called '%s'." launch-configuration-name auto-scaling-group-name))
    (success (update-in parameters [:new-state] merge {:launch-configuration-name launch-configuration-name
                                                       :auto-scaling-group-name auto-scaling-group-name}))))

(defn get-image-details
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state (:new-state parameters)
        image-id (get-in state [:image-details :id])]
    (try
      (log/write (format "Retrieving image details for '%s'." image-id))
      (if-let [image (aws/image image-id environment region)]
        (success (update-in parameters [:new-state :image-details] merge (util/image-details image)))
        (do
          (log/write (format "No image found with ID '%s'. Is the ID correct, or is the image not shared with the relevant account?" image-id))
          (error-with (ex-info "Image not found." {:type ::missing-image
                                                   :image-id image-id}))))
      (catch Exception e
        (error-with e)))))

(defn verify-image
  [{:keys [parameters]}]
  (let [{:keys [application]} parameters
        state (:new-state parameters)
        {:keys [image-details]} state
        {:keys [image-name]} image-details]
    (try
      (log/write (format "Checking that image name '%s' matches application '%s'." image-name application))
      (let [image-application (:application image-details)]
        (if (= image-application application)
          (success parameters)
          (error-with (ex-info "Image name does not match application being deployed." {:type ::mismatched-image
                                                                                        :application application
                                                                                        :image-name image-name}))))
      (catch Exception e
        (error-with e)))))

(defn check-for-embargo
  [{:keys [parameters]}]
  (let [{:keys [environment]} parameters
        state (:new-state parameters)
        {:keys [image-details]} state
        image-id (get-in state [:image-details :id])
        {:keys [tags]} image-details
        {:keys [embargo]} tags]
    (if (and (seq embargo) (some #(= environment %) (str/split embargo #",")))
      (do
        (log/write (format "Image '%s' is embargoed in '%s'" image-id environment))
        (error-with (ex-info "Attempt to use embargoed image." {:type ::embargoed-image
                                                                :image-id image-id
                                                                :environment environment
                                                                :embargo embargo})))
      (success parameters))))

(defn check-instance-type-compatibility
  [{:keys [parameters]}]
  (let [state (:new-state parameters)
        {:keys [image-details tyranitar]} state
        {:keys [virt-type]} image-details
        {:keys [deployment-params]} tyranitar
        {:keys [instance-type]} deployment-params
        allowed-instances (v/allowed-instances virt-type)]
    (if (contains? allowed-instances instance-type)
      (success parameters)
      (error-with (ex-info (format "Instance type %s is incompatible with virtualisation type %s" instance-type virt-type) {:type ::unknown-virtualisation-type})))))

(defn check-contact-property
  [{:keys [parameters]}]
  (let [{:keys [application]} parameters
        state (:new-state parameters)
        {:keys [onix]} state
        {:keys [contact]} onix]
    (log/write (format "Checking that the 'contact' property is set for application '%s' in Lister." application))
    (if contact
      (success parameters)
      (error-with (ex-info "Contact property is not set in Lister metadata." {:type ::contact-missing})))))

(defn check-pedantic-configuration
  [{:keys [parameters]}]
  (let [{:keys [application environment]} parameters]
    (log/write (format "Checking that Pedantic configuration exists for application '%s' in environment '%s'." application environment))
    (if (or (= environment "poke") (= environment "prod"))
      (try
        (if (pedantic/configuration environment application)
          (success parameters)
          (error-with (ex-info "Pedantic configuration missing for the application." {:type ::missing-pedantic
                                                                                      :application application
                                                                                      :environment environment})))
        (catch Exception e
          (error-with e)))
      (success parameters))))

(defn create-block-device-mappings
  [{:keys [parameters]}]
  (let [state (:new-state parameters)
        {:keys [image-details tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [instance-type volumes]} deployment-params
        {:keys [block-devices instance-stores root]} volumes
        {:keys [virt-type]} image-details]
    (log/write (format "Creating block device mappings."))
    (success (assoc-in parameters [:new-state :block-device-mappings] (dev/create-mappings root (or instance-stores (get-in instance-info [instance-type :instance-stores])) block-devices virt-type)))))

(defn add-required-security-groups
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state (:new-state parameters)
        {:keys [tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [selected-security-groups]} deployment-params]
    (log/write (format "Adding required %s [%s]." (util/pluralise (count selected-security-groups) "security group") (str/join ", " required-security-group-names)))
    (success (assoc-in parameters [:new-state :tyranitar :deployment-params :selected-security-groups] (apply merge (or selected-security-groups []) required-security-group-names)))))

(defn- is-security-group-id?
  [name]
  (re-find #"^sg-" name))

(defn map-security-group-ids
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state (:new-state parameters)
        {:keys [tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [selected-security-groups]} deployment-params
        names (remove is-security-group-id? selected-security-groups)
        ids (filter is-security-group-id? selected-security-groups)]
    (log/write "Mapping security group names to their IDs.")
    (try
      (let [groups-by-name (aws/security-groups-by-name environment region)
            groups-by-id (aws/security-groups-by-id environment region)
            named-groups (apply merge (map (fn [name] {name (get groups-by-name name)}) names))
            id-groups (apply merge (map (fn [id] {id (get groups-by-id id)}) ids))
            unknown-named-groups (or (keys (filter (fn [[_ v]] (nil? v)) named-groups)) [])
            unknown-id-groups (or (keys (filter (fn [[_ v]] (nil? v)) id-groups)) [])
            unknown-groups (sort (apply merge unknown-named-groups unknown-id-groups))]
        (if-not (seq unknown-groups)
          (success (assoc-in parameters [:new-state :selected-security-group-ids] (sort (apply merge (map :group-id (vals named-groups)) (map :group-id (vals id-groups))))))
          (error-with (ex-info "Unknown security groups." {:type ::unknown-security-groups
                                                           :security-groups unknown-groups}))))
      (catch Exception e
        (error-with e)))))

(defn verify-load-balancers
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state (:new-state parameters)
        {:keys [tyranitar]} state
        {:keys [deployment-params]} tyranitar]
    (if-let [selected-load-balancers (seq (:selected-load-balancers deployment-params))]
      (do
        (log/write "Verifying specified load balancers exist.")
        (let [found-load-balancers (aws/load-balancers-with-names environment region selected-load-balancers)]
          (if (= (count found-load-balancers) (count selected-load-balancers))
            (success parameters)
            (error-with (ex-info "One or more load balancers could not be found." {:type ::missing-load-balancers
                                                                                   :load-balancers (filter (fn [[_ v]] (nil? v)) found-load-balancers)})))))
      (success parameters))))

(defn check-for-deleted-load-balancers
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state (:previous-state parameters)
        {:keys [tyranitar]} state
        {:keys [deployment-params]} tyranitar]
    (if-let [selected-load-balancers (seq (:selected-load-balancers deployment-params))]
      (do
        (log/write "Checking for load balancers which were previously used but have been deleted.")
        (let [found-load-balancers (aws/load-balancers-with-names environment region selected-load-balancers)]
          (success (assoc-in parameters [:previous-state :tyranitar :deployment-params :selected-load-balancers] (keys (util/remove-nil-values found-load-balancers))))))
      (success parameters))))

(defn- has-required-zones?
  [subnets availability-zones]
  (let [subnet-zones (apply hash-set (map :availability-zone subnets))]
    (set/subset? (apply hash-set availability-zones) subnet-zones)))

(defn populate-subnets
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state (:new-state parameters)
        {:keys [availability-zones tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [selected-zones subnet-purpose]} deployment-params
        availability-zones (map #(str region %) selected-zones)]
    (log/write (format "Locating subnets with purpose '%s'." subnet-purpose))
    (if-let [subnets (aws/filter-by-availability-zones availability-zones (aws/subnets-by-purpose environment region subnet-purpose))]
      (if (has-required-zones? subnets availability-zones)
        (success (-> parameters
                     (assoc-in [:new-state :selected-subnets] (map :subnet-id subnets))
                     (assoc-in [:new-state :availability-zones] (map :availability-zone subnets))))
        (error-with (ex-info (format "Availability zone requirement of %s cannot be satisfied by subnets with purpose '%s'." selected-zones subnet-purpose) {:type ::zone-to-subnet-mismatch
                                                                                                                                                             :purpose subnet-purpose
                                                                                                                                                             :selected-zones selected-zones})))
      (error-with (ex-info "No subnets found for purpose." {:type ::no-subnets
                                                            :purpose subnet-purpose})))))

(defn populate-vpc-zone-identifier
  [{:keys [parameters]}]
  (let [{:keys [environment region]} parameters
        state (:new-state parameters)
        {:keys [selected-subnets]} state]
    (log/write "Populating VPC zone identifier.")
    (success (assoc-in parameters [:new-state :vpc-zone-identifier] (str/join "," selected-subnets)))))

(defn populate-termination-policies
  [{:keys [parameters]}]
  (let [{:keys [region]} parameters
        state (:new-state parameters)
        {:keys [tyranitar]} state
        {:keys [deployment-params]} tyranitar
        {:keys [termination-policy]} deployment-params]
    (log/write "Populating termination policies.")
    (success (assoc-in parameters [:new-state :termination-policies] (util/list-from termination-policy)))))

(defn to-auto-scaling-group-tag
  [auto-scaling-group-name [k v]]
  {:key (name k)
   :value v
   :propagate-at-launch true
   :resource-type "auto-scaling-group"
   :resource-id auto-scaling-group-name})

(defn create-auto-scaling-group-tags
  [{:keys [parameters]}]
  (let [{:keys [application environment user]} parameters
        state (:new-state parameters)
        {:keys [auto-scaling-group-name image-details onix]} state
        {:keys [version]} image-details
        name (format "%s-%s-%s" application environment version)]
    (log/write "Creating auto scaling group tags.")
    (success (assoc-in parameters [:new-state :auto-scaling-group-tags] (map (partial to-auto-scaling-group-tag auto-scaling-group-name) {:Application application
                                                                                                                                          :Contact (:contact onix)
                                                                                                                                          :DeployedBy user
                                                                                                                                          :DeployedOn (str (time/now))
                                                                                                                                          :Environment environment
                                                                                                                                          :Name name
                                                                                                                                          :Version version})))))

(defn generate-user-data
  [{:keys [parameters]}]
  (log/write "Generating user data.")
  (success (assoc-in parameters [:new-state :user-data] (ud/create-user-data parameters))))

(defn complete-deployment-preparation
  [{:keys [parameters]}]
  (let [{:keys [application environment]} parameters]
    (log/write (format "Done preparing deployment of '%s' to '%s'." application environment))
    (success parameters)))

(defn start-deployment
  [{:keys [parameters]}]
  (let [{:keys [application environment undo]} parameters]
    (if-not undo
      (do
        (hubot/speak-about-deployment-start parameters)
        (log/write (format "Starting deployment of '%s' to '%s'." application environment)))
      (do
        (hubot/speak-about-deployment-undo parameters)
        (log/write (format "Starting undo of '%s' in '%s'." application environment))))
    (success (-> parameters
                 (assoc :phase "deployment")
                 (assoc :status "running")
                 (dissoc :end)))))

(defn complete-deployment
  [{:keys [parameters]}]
  (let [{:keys [application environment undo]} parameters]
    (if-not undo
      (log/write (format "Deployment of '%s' to '%s' complete." application environment))
      (log/write (format "Undo of '%s' in '%s' complete." application environment)))
    (success (assoc parameters :end (time/now)))))
