(ns exploud.setup
  "## Setting up our application"
  (:require [cheshire.custom :as json]
            [clojure.java.io :as io]
            [clojure.string :as cs :only [split]]
            [clojure.tools.logging :refer [info warn error]]
            [exploud
             [elasticsearch :as elasticsearch]
             [messages :as messages]
             [redis :as redis]
             [web :as web]]
            [environ.core :refer [env]]
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

(defn configure-joda
  "Configures Joda Time to use UTC as the default timezone (in case someone
   hasn't included it in the JVM args."
  []
  (json/add-encoder org.joda.time.DateTime (fn [dt jg] (.writeString jg (str dt))))
  (DateTimeZone/setDefault DateTimeZone/UTC))

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
  (configure-logging)
  (start-graphite-reporting)
  (redis/init messages/handler)
  (elasticsearch/init))

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
  (.addShutdownHook (Runtime/getRuntime) (Thread. shutdown-agents))
  (start))
