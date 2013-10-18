(ns exploud.deployment
  "Creating and managing deployment chains."
  (:use [exploud.asgard_new :as asgard])
  (:import java.util.UUID))

(defn generate-id
  "Create a random ID for a deployment or task."
  []
  (str (UUID/randomUUID)))

(defn new-task
  "Creates a new task with a random ID, the given `:action` and a `:status` of `pending`."
  [action]
  {:id (generate-id)
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

(defn start-deployment
  "Starts the deployment."
  [deployment]
  )

(defn task-completed
  "Function called when a task has completed. Deals with moving the deployment to the next phase."
  [ticket-id task]
  )

(defn create-asg
  "Given the deployment and the task we're currently performing determine whether we should be creating the next ASG or a brand new one, then do it."
  [{:keys [ami application environment id region]} task]
  (if-let [asg (asgard/last-auto-scaling-group region (str application "-" environment))]
    (let [old-asg-name (:autoScalingGroupName asg)]
      (asgard/create-next-asg region application environment ami hash id task))
    (asgard/create-new-asg region application environment ami hash id task)))
