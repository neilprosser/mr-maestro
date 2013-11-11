(ns exploud.deployment
  "## Creating and managing deployment chains"
  (:require [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [dire.core :refer [with-handler!
                               with-post-hook!
                               with-pre-hook!
                               with-precondition!]]
            [exploud
             [asgard :as asgard]
             [healthchecks :as health]
             [store :as store]
             [tyranitar :as tyr]
             [util :as util]]))

;; # Concerning tasks

(defn new-task
  "Creates a new task with a random ID, the given `:action` and a `:status` of
   `pending`.

   Accepts a map `extras` which will be merged with the generated map."
  [action & [extras]]
  (merge extras
         {:id (util/generate-id)
          :action action
          :status "pending"}))

(defn create-standard-deployment-tasks
  "Creates a standard deployment using the following actions:

   - __create-asg__ - create either the next ASG or a new starting ASG for the
     application
   - __wait-for-instance-health__ - wait until all instances in the newly-
     created ASG are passing their healthchecks (by showing a 200 status)
   - __enable-asg__ - enable traffic to the newly-created ASG
   - __wait-for-elb-health__ - if adding instances to any ELBs, wait until
     the minimum number of instances in the newly-created ASG are listed as
     healthy in the ELBs, if not adding instances to any ELBs this is a no-op
   - __disable-asg__ - disable traffic to the old ASG, if there was no previous
     ASG this is a no-op
   - __delete-asg__ - delete the old ASG, terminating any instances within it,
     if there was no previous ASG this is a no-op"
  []
  [(new-task :create-asg)
   (new-task :wait-for-instance-health)
   (new-task :enable-asg)
   (new-task :wait-for-elb-health)
   (new-task :disable-asg)
   (new-task :delete-asg)])

(defmulti create-undo
  (fn [task]
    (:action task)))

(defmethod create-undo
  :create-asg
  [task]
  [(new-task :delete-asg {:undo true})])

(defmethod create-undo
  :wait-for-instance-health
  [task]
  [])

(defmethod create-undo
  :enable-asg
  [task]
  [(new-task :disable-asg {:undo true})])

(defmethod create-undo
  :wait-for-elb-health
  [task]
  [])

(defmethod create-undo
  :disable-asg
  [task]
  [(new-task :enable-asg {:undo true})
   (new-task :wait-for-elb-health {:undo true})])

(defmethod create-undo
  :delete-asg
  [task]
  [(new-task :create-asg {:undo true})
   (new-task :wait-for-instance-health {:undo true})])

(defn- pending?
  "Whether a task has a `:status` of pending."
  [{:keys [status]}]
  (= "pending" status))

(defn create-undo-tasks
  "Takes a list of tasks and attempts to create a suitable undo plan which will
   reverse all actions already completed or started."
  [tasks]
  (let [active-tasks (vec (remove pending? tasks))
        undo-tasks (reverse (remove nil? (flatten (map #(reverse (create-undo %)) active-tasks))))]
    (apply merge active-tasks undo-tasks)))

(defn task-after
  "Given a deployment and a task ID will find the task which occurs after the
   one with an `:id` of `task-id` in the `:tasks` of `deployment`. It returns
   either the next task or `nil` if the given ID was last."
  [{:keys [tasks]} task-id]
  (->> tasks
       reverse
       (take-while (fn [{:keys [id]}] (not= id task-id)))
       last))

;; Pre-hook attached to `task-after` to log parameters.
(with-pre-hook! #'task-after
  (fn [d id]
    (log/debug "Getting task after" id "in" d)))

(with-post-hook! #'task-after
  (fn [t]
    (log/debug "Task after came back as" t)))

(defn wait-for-instance-health?
  "If the "
  [{:keys [parameters]}]
  (if-let [min (or (:min parameters) asgard/default-minimum)]
    (pos? min)
    false))

;; Pre-hook attached to `wait-for-instance-health?` to log parameters.
(with-pre-hook! #'wait-for-instance-health?
  (fn [{:keys [parameters]}]
    (log/debug "Working out whether we should wait for instance health of"
               parameters)))

(defn check-elb-health?
  "If the `:parameters` of `deployment` have both a `selectedLoadBalancers`
   value and `healthCheckType` of `ELB` then we should be checking the health
   of the ELB. Otherwise, we're not going to do anything. They're on their own."
  [{:keys [parameters]}]
  (and (pos? (count (util/list-from (:selectedLoadBalancers parameters))))
       (= "ELB" (:healthCheckType parameters))))

;; Pre-hook attached to `check-elb-health?` to log parameters.
(with-pre-hook! #'check-elb-health?
  (fn [{:keys [parameters]}]
    (log/debug "Working out whether we should check ELB health of"
               parameters)))

;; We're going to need these before we can defn them.
(declare task-finished)
(declare task-timed-out)
(declare finish-deployment)

(defmulti start-task*
  (fn [deployment {:keys [action] :as task}]
    (keyword action)))

(defmethod start-task*
  :create-asg
  [deployment task]
  (asgard/create-auto-scaling-group (assoc deployment
                                      :task task
                                      :completed-fn task-finished
                                      :timed-out-fn task-timed-out)))

(defmethod start-task*
  :wait-for-instance-health
  [{:keys [application environment hash region] deployment-id :id :as deployment} task]
  (if (wait-for-instance-health? deployment)
    (let [service-properties (tyr/application-properties
                              environment application hash)
          port (:service.port service-properties 8080)
          healthcheck (:service.healthcheck.path service-properties
                                                 "/healthcheck")]
      (health/wait-until-asg-healthy
       environment
       region
       (get-in deployment [:parameters :newAutoScalingGroupName])
       (or (get-in deployment [:parameters :min]) asgard/default-minimum)
       port healthcheck deployment-id task
       task-finished task-timed-out))
    (let [task (assoc (util/append-to-task-log "Skipping instance healthcheck" task)
                 :status "skipped")]
      (task-finished deployment-id task))))

(defmethod start-task*
  :enable-asg
  [{:keys [environment region] deployment-id :id :as deployment} task]
  (asgard/enable-asg environment region
                     (get-in deployment [:parameters :newAutoScalingGroupName])
                     deployment-id task task-finished task-timed-out))

(defmethod start-task*
  :wait-for-elb-health
  [{:keys [environment region] deployment-id :id :as deployment} task]
  (if (check-elb-health? deployment)
    (let [elb-names (util/list-from
                     (get-in deployment
                             [:parameters :selectedLoadBalancers]))
          asg-name (get-in deployment
                           [:parameters :newAutoScalingGroupName])]
      (health/wait-until-elb-healthy environment region elb-names asg-name
                                     deployment-id task task-finished
                                     task-timed-out))
    (let [task (assoc (util/append-to-task-log "Skipping ELB healthcheck" task)
                 :status "skipped")]
      (task-finished deployment-id task))))

(defmethod start-task*
  :disable-asg
  [{:keys [environment region] deployment-id :id :as deployment} task]
  (if-let [asg (get-in deployment [:parameters
                                   :oldAutoScalingGroupName])]
    (asgard/disable-asg environment region asg deployment-id task
                        task-finished task-timed-out)
    (let [task (assoc (util/append-to-task-log "No old ASG to disable" task)
                 :status "skipped")]
      (task-finished deployment-id task))))

(defmethod start-task*
  :delete-asg
  [{:keys [environment region] deployment-id :id :as deployment} task]
  (if-let [asg (get-in deployment [:parameters
                                   :oldAutoScalingGroupName])]
    (asgard/delete-asg
     environment region asg deployment-id task task-finished task-timed-out)
    (let [task (assoc (util/append-to-task-log "No old ASG to delete")
                 :status "skipped")]
      (task-finished deployment-id task))))

(defn start-task
  "Entry point for starting tasks, makes sure there's a `:start` date on it
   and gets things moving."
  [deployment task]
  (start-task* deployment (assoc task :start (time/now))))

(defn successful?
  "Whether the task has finished successfully."
  [{:keys [status]}]
  (or (= "completed" status)
      (= "skipped" status)))

(defn task-finished
  "Function called when a task has completed. Deals with moving the deployment
   to the next phase."
  [deployment-id {task-id :id :as task}]
  (store/store-task deployment-id (assoc task :end (time/now)))
  (let [deployment (store/get-deployment deployment-id)]
    (if (successful? task)
      (if-let [next-task (task-after deployment task-id)]
        (start-task deployment next-task)
        (finish-deployment deployment))
      (finish-deployment deployment))))

;; Pre-hook attached to `task-finished` to log parameters.
(with-pre-hook! #'task-finished
  (fn [deployment-id task]
    (log/debug "Task" task "finished for deployment with ID" deployment-id)))

(defn task-timed-out
  "Function called when a task has timed-out. Deals with the repercussions of
   that."
  [deployment-id task]
  (store/store-task deployment-id (assoc task
                                    :end (time/now)
                                    :status "failed"))
  nil)

;; Pre-hook attached to `task-timed-out` to log parameters.
(with-pre-hook! #'task-timed-out
  (fn [deployment-id task]
    (log/debug "Task" task "timed-out for deployment with ID" deployment-id)))

;; # Concerning deployments

(defn prepare-deployment
  "Prepares a deployment of the `application` in an `environment` within the
   given `region`. It'll mark the deployment as being done by `user` and will
   use `ami` when telling what Asgard should deploy then store it. Accepts an
   optional commit `hash` which will be used for grabbing properties from
   Tyranitar. If hash is `nil` the latest properties will be used.

   Will return the newly-created deployment."
  [region application environment user ami hash message]
  (let [hash (or hash (tyr/last-commit-hash environment application))
        parameters (tyr/deployment-params environment application hash)
        tasks (create-standard-deployment-tasks)
        deployment {:ami ami
                    :application application
                    :created (time/now)
                    :environment environment
                    :hash hash
                    :id (util/generate-id)
                    :message message
                    :parameters parameters
                    :region region
                    :tasks tasks
                    :user user}]
    (store/store-deployment deployment)
    deployment))

;; Precondition attached to `prepare-deployment` to check that the AMI
;; application matches the application being deployed.
(with-precondition! #'prepare-deployment
  :ami-name-matches
  (fn [region application _ _ ami _ _]
    (let [ami-application (or (get-in (asgard/image region ami) [:image :name]) "")]
      (= application (second (re-find #"^ent-([^-]+)-" ami-application))))))

;; Handler attached to `prepare-deployment` to throw an error if the AMI
;; application doesn't match the one being deployed.
(with-handler! #'prepare-deployment
  {:precondition :ami-name-matches}
  (fn [e region application _ _ ami _ _]
    (throw (ex-info "Image does not match application" {:type ::mismatched-image
                                                        :region region
                                                        :application application
                                                        :ami ami}))))

(defn prepare-rollback
  "Prepares a rollback of the `application` in an `environment` within the
   given `region`. It'll mark the deployment as being done by `user`. A rollback
   looks for the penultimate completed deployment which matches the given
   criteria and creates a copy of it.

   Will return the newly-created deployment."
  [region application environment user message]
  (if-let [penultimate (first (store/get-completed-deployments {:application application
                                                                :environment environment
                                                                :size 1
                                                                :from 1}))]
    (let [{:keys [ami hash]} penultimate]
      (prepare-deployment region application environment user ami hash message))
    (throw (ex-info "No penultimate completed deployment to rollback to"
                    {:type ::no-rollback
                     :region region
                     :application application
                     :environment environment}))))

(defn prepare-undo
  "Takes a deployment and edits the list of tasks to reverse tasks that have been started.

   Returns the edited deployment."
  [{:keys [tasks] :as deployment}]
  (if (zero? (count tasks))
    deployment
    (let [updated-tasks (create-undo-tasks tasks)]
      (store/store-deployment (assoc deployment :tasks updated-tasks))
      nil)))

(defn start-deployment
  "Kicks off the first task of the deployment with `deployment-id`."
  [deployment-id]
  (let [deployment (store/get-deployment deployment-id)
        first-task (first (:tasks deployment))
        deployment (assoc deployment :start (time/now))]
    (store/store-deployment deployment)
    (start-task deployment first-task)
    nil))

(defn finish-deployment
  "Puts an `:end` date on the deployment and we all breathe a sigh of relief. Unless
   it failed of course."
  [deployment]
  (store/store-deployment (assoc deployment :end (time/now)))
  nil)
