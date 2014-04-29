(ns exploud.redis
  (:require [clojure.string :as str]
            [environ.core :refer [env]]
            [taoensso.carmine :as car :refer [wcar]]
            [taoensso.carmine.message-queue :as car-mq]))

(def ^:private in-progress-key
  "exploud:deployments:in-progress")

(def ^:private paused-key
  "exploud:deployments:paused")

(def ^:private awaiting-pause-key
  "exploud:deployments:awaiting-pause")

(def ^:private lock-key
  "exploud:lock")

(def ^:private scheduled-tasks-key
  "scheduled-tasks")

(def worker
  (atom nil))

(def ^:dynamic *dummy-connection*
  nil)

(def redis-connection
  (atom nil))

(defmacro using-redis
  [& body]
  `(if-not *dummy-connection*
     (do
       (wcar @redis-connection ~@body))
     (do
       ~@body)))

(defn enqueue
  [task]
  (using-redis (car-mq/enqueue scheduled-tasks-key task)))

(defn locked?
  []
  (using-redis (car/get lock-key)))

(defn lock
  []
  (using-redis (car/set lock-key "true")))

(defn unlock
  []
  (using-redis (car/del lock-key)))

(defn- field-for
  [application environment region]
  (str/join "-" [application environment region]))

(defn- create-description-with-id
  [[k id]]
  (let [[application environment region] (str/split k #"-" 3)]
    {:application application
     :environment environment
     :id id
     :region region}))

(defn- create-description
  [k]
  (let [[application environment region] (str/split k #"-" 3)]
    {:application application
     :environment environment
     :region region}))

(defn paused?
  [application environment region]
  (using-redis (car/hget paused-key (field-for application environment region))))

(defn paused
  []
  (map create-description-with-id (apply hash-map (using-redis (car/hgetall paused-key)))))

(defn pause
  [application environment id region]
  (pos? (using-redis (car/hsetnx paused-key (field-for application environment region) id))))

(defn resume
  [application environment region]
  (pos? (using-redis (car/hdel paused-key (field-for application environment region)))))

(defn pause-registered?
  [application environment region]
  (pos? (using-redis (car/sismember awaiting-pause-key (field-for application environment region)))))

(defn awaiting-pause
  []
  (map create-description (using-redis (car/smembers awaiting-pause-key))))

(defn register-pause
  [application environment region]
  (pos? (using-redis (car/sadd awaiting-pause-key (field-for application environment region)))))

(defn unregister-pause
  [application environment region]
  (pos? (using-redis (car/srem awaiting-pause-key (field-for application environment region)))))

(defn in-progress?
  [application environment region]
  (using-redis (car/hget in-progress-key (field-for application environment region))))

(defn in-progress
  []
  (map create-description-with-id (apply hash-map (using-redis (car/hgetall in-progress-key)))))

(defn begin-deployment
  [{:keys [application environment id region]}]
  (pos? (using-redis (car/hsetnx in-progress-key (field-for application environment region) id))))

(defn end-deployment
  [{:keys [application environment region]}]
  (pos? (using-redis (car/hdel in-progress-key (field-for application environment region)))))

(defn queue-status
  []
  (car-mq/queue-status @redis-connection scheduled-tasks-key))

(defn init
  [handler]
  (reset! redis-connection {:pool {}
                            :spec {:host (env :redis-host "localhost")
                                   :port (Integer/valueOf (str (env :redis-port "6379")))}})
  (reset! worker (car-mq/worker @redis-connection scheduled-tasks-key
                                {:handler handler
                                 :lock-ms 60000
                                 :eoq-backoff-ms 200
                                 :n-threads (Integer/valueOf (env :redis-queue-threads "1"))
                                 :throttle-ms 200})))
