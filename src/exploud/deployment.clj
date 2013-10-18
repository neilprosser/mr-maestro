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

   - __create-new-asg__ - create either the next ASG or a new starting ASG for the application
   - __wait-for-health__ - wait until all instances in the newly-created ASG are healthy
   - __enable-asg__ - enable traffic to the newly-created ASG
   - __disable-asg__ - disable traffic to the old ASG
   - __delete-asg__ - delete the old ASG, terminating any instances within it"
  []
  [(new-task :create-next-asg)
   (new-task :wait-for-health)
   (new-task :enable-asg)
   (new-task :disable-asg)
   (new-task :delete-asg)])

(defn prepare-deployment
  "Prepares a deployment for the application in a given region. This will create the correct deployment tasks (based on the state of the application) and pre-populate any parameters we can provide to the tasks. If the application and environment combination already has an ASG then the next one will be created, otherwise we'll create a new ASG."
  [region application-name environment]
  (if-let [asg (asgard/last-auto-scaling-group region (str application-name "-" environment))]
    nil
    nil))

(defn start-deployment
  "Starts the deployment."
  [deployment]
  )

(defn task-completed
  "Function called when a task has completed. Deals with moving the deployment to the next phase."
  [ticket-id task]
  )
