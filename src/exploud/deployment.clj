(ns exploud.deployment
  "Creating and managing deployment chains."
  (:require [exploud
             [asgard_new :as asgard]
             [store :as store]
             [tyranitar :as tyr]
             [util :as util]]))

;; # Concerning tasks

(defn new-task
  "Creates a new task with a random ID, the given `:action` and a `:status` of `pending`."
  [action]
  {:id (util/generate-id)
   :action action
   :status "pending"})

(defn create-standard-deployment-tasks
  "Creates a standard deployment using the following actions:

   - __create-asg__ - create either the next ASG or a new starting ASG for the application
   - __wait-for-health__ - wait until all instances in the newly-created ASG are healthy
   - __enable-asg__ - enable traffic to the newly-created ASG
   - __disable-asg__ - disable traffic to the old ASG
   - __delete-asg__ - delete the old ASG, terminating any instances within it"
  []
  [(new-task :create-asg)
   (new-task :wait-for-health)
   (new-task :enable-asg)
   (new-task :disable-asg)
   (new-task :delete-asg)])

(defn prepare-deployment-tasks
  "Prepares a deployment for the application in a given region. This will create the deployment tasks and pre-populates any parameters we can provide to the first task."
  [region application-name environment]
  (let [tasks (create-standard-deployment-tasks)]
    (if-let [asg (asgard/last-auto-scaling-group region (str application-name "-" environment))]
      (let [old-asg-name (:autoScalingGroupName asg)]
        (assoc-in tasks [0 :params :old-asg] old-asg-name))
      tasks)))

(defn task-after
  "Given a deployment and a task ID will find the task which occurs after the one with an `:id` of `task-id` in the `:tasks` of `deployment`. It returns either the next task or `nil` if the given ID was last."
  [{:keys [tasks]} task-id]
  (-> (partition-by (fn [t] (= (:id t) task-id)) tasks)
      (nth 2 nil)
      first))

;; We're going to need these before we can defn them.
(declare task-finished)
(declare task-timed-out)
(declare finish-deployment)

(defn start-task
  "Starts the given task based on its `:action`."
  [deployment {:keys [action] :as task}]
  (cond (= action :create-asg)
        (asgard/create-auto-scaling-group deployment (assoc task :start (util/now-string)) task-finished task-timed-out)
        :else (throw (ex-info "Unrecognised action." {:type ::unrecogized-action
                                                      :action action}))))

(defn task-finished
  "Function called when a task has completed. Deals with moving the deployment to the next phase."
  [deployment-id {task-id :id}]
  (let [deployment (store/get-deployment deployment-id)
        next-task (task-after deployment task-id)]
    (if next-task
      (start-task deployment next-task)
      (finish-deployment deployment))))

(defn task-timed-out
  "Function called when a task has timed-out. Deals with the repercussions of that."
  [deployment-id task]
  ;; TODO - FINISH HIM!
  )

;; # Concerning deployments

(defn prepare-deployment
  "Prepares a deployment of the `application` in an `environment` within the given `region`. It'll mark the deployment as being done by `user` and will use `ami` when telling what Asgard should deploy then store it.

   Will return the newly-created deployment."
  [region application environment user ami]
  (let [hash (tyr/last-commit-hash environment application)
        parameters (tyr/deployment-params environment application hash)
        tasks (create-standard-deployment-tasks)
        deployment {:ami ami
                    :application application
                    :created (util/now-string)
                    :environment environment
                    :hash hash
                    :id (util/generate-id)
                    :parameters parameters
                    :region region
                    :tasks tasks
                    :user user}]
    (store/store-deployment deployment)
    deployment))

(defn start-deployment
  "Kicks off the first task of the deployment with `deployment-id`."
  [deployment-id]
  (let [deployment (store/get-deployment deployment-id)
        first-task (first (:tasks deployment))]
    (store/store-deployment (assoc deployment :start (util/now-string)))
    (start-task first-task)
    nil))

(defn finish-deployment
  "Puts an `:end` date on the deployment and we all breathe a sigh of relief!"
  [deployment]
  (store/store-deployment (assoc deployment :end (util/now-string)))
  nil)
