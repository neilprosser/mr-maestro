(ns exploud.setup
  "## Setting up our application"
  (:require [cheshire.custom :as json]
            [clojure.java.io :as io]
            [clojure.string :as cs :only (split)]
            [clojure.tools.logging :refer (info warn error)]
            [exploud
             [asgard :as asgard]
             [deployment :as deployment]
             [store :as store]
             [web :as web]]
            [environ.core :refer [env]]
            [monger
             [collection :as mcol :only (ensure-index)]
             [core :as mc :only (connect! mongo-options
                                          server-address use-db!)]]
            [nokia.adapter.instrumented-jetty :refer [run-jetty]])
  (:import (java.lang Integer Throwable)
           (java.util.concurrent TimeUnit)
           (java.util.logging LogManager)
           (com.yammer.metrics Metrics)
           (com.yammer.metrics.core MetricName)
           (com.ovi.common.metrics HostnameFactory)
           (com.ovi.common.metrics.graphite GraphiteName
                                            GraphiteReporterFactory
                                            ReporterState)
           (org.joda.time DateTimeZone)
           (org.slf4j.bridge SLF4JBridgeHandler))
  (:gen-class))

(defn read-file-to-properties
  "Reads the file at `file-name` to an instance of `java.util.Properties`."
  [file-name]
  (with-open [^java.io.Reader reader (io/reader file-name)]
    (let [props (java.util.Properties.)]
      (.load props reader)
      (into {} (for [[k v] props] [k v])))))

(defn configure-logging
  "Route all `java.util.logging` log statements to `slf4j`."
  []
  (.reset (LogManager/getLogManager))
  (SLF4JBridgeHandler/install))

(defn build-server-addresses
  "Takes a comma-separated list of `host:port` pairs and breaks them up into
   Mongo server addresses."
  [comma-sep-hosts]
  (map (fn [[h p]] (mc/server-address h (Integer/parseInt p))) (map #(cs/split % #":") (cs/split comma-sep-hosts #","))))

(defn configure-joda
  "Configures Joda Time to use UTC as the default timezone (in case someone
   hasn't included it in the JVM args."
  []
  (json/add-encoder org.joda.time.DateTime (fn [dt jg] (.writeString jg (str dt))))
  (DateTimeZone/setDefault DateTimeZone/UTC))

(defn configure-mongo-conn-pool
  "Configures the Mongo connection pool."
  []
  (let [^MongoOptions opts (mc/mongo-options :threads-allowed-to-block-for-connection-multiplier 10
                                             :connections-per-host (Integer/parseInt (env :mongo-connections-max))
                                             :max-wait-time 120000
                                             :connect-timeout 30000
                                             :socket-timeout 10000
                                             :socket-keep-alive false)
        sa (build-server-addresses (env :mongo-hosts))]
    (mc/connect! sa opts)))

(defn configure-mongo-db
  "Configures Mongo to use the right database."
  []
  (mc/use-db! "exploud"))

(defn bootstrap-mongo
  "Makes sure that all the indexes we want are present on our collections."
  []
  (mcol/ensure-index "deployments" (array-map "tasks.status" 1))
  (mcol/ensure-index "deployments" (array-map "start" -1 "end" -1 "name" 1)))

(defn start-graphite-reporting
  "Starts Graphite reporting."
  []
  (let [graphite-prefix (new GraphiteName (into-array Object [(env :environment-name)
                                                              (env :service-name)
                                                              (HostnameFactory/getHostname)]))]
    (GraphiteReporterFactory/create
     (env :environment-entertainment-graphite-host)
     (Integer/parseInt (env :environment-entertainment-graphite-port))
     graphite-prefix
     (Integer/parseInt (env :service-graphite-post-interval))
     (TimeUnit/valueOf (env :service-graphite-post-unit))
     (ReporterState/valueOf (env :service-graphite-enabled)))))

(defn pick-up-tasks
  "Picks up incomplete tasks and schedules them for tracking.

   The intention is that even if exploud is redeployed while another
   deployment is going on, that deployment can be picked up and carry on."
  []
  (doseq [deployment (store/deployments-with-incomplete-tasks)]
    (let [{:keys [id tasks]} deployment]
      (doseq [task tasks]
        (asgard/track-until-completed id task (* 1 60 60) deployment/task-finished deployment/task-timed-out)))))

(def version
  "Gets the version of the application from the project properties."
  (delay
   (if-let [path (.getResource (ClassLoader/getSystemClassLoader) "META-INF/maven/exploud/exploud/pom.properties")]
     ((read-file-to-properties path) "version")
     "localhost")))

(defn setup
  "Sets up the application."
  []
  (web/set-version! @version)
  (configure-joda)
  (configure-mongo-conn-pool)
  (configure-mongo-db)
  (bootstrap-mongo)
  (configure-logging)
  (start-graphite-reporting)
  (comment (pick-up-tasks)))

(def server
  "Our trusty server."
  (atom nil))

(defn start-server
  "Starts the server."
  []
  (run-jetty #'web/app {:port (Integer. (env :service-port))
                        :join? false
                        :stacktraces? (not (Boolean/valueOf (env :service-production)))
                        :auto-reload? (not (Boolean/valueOf (env :service-production)))}))

(defn start
  "Sets up our application and starts the server."
  []
  (do
    (setup)
    (reset! server (start-server))))

(defn stop
  "Stops the server."
  []
  (if-let [server @server] (.stop server)))

(defn -main
  "The entry point for the application."
  [& args]
  (start))
