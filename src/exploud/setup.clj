(ns exploud.setup
  (:require [clojure.java.io :as io]
            [clojure.string :as cs :only (split)]
            [clojure.tools.logging :refer (info warn error)]
            [exploud
             [asgard :as asg]
             [store :as store]
             [web :as web]]
            [environ.core :refer [env]]
            [monger
             [collection :as mcol :only (ensure-index)]
             [core :as mc :only (connect! mongo-options server-address use-db!)]]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import (java.lang Integer Throwable)
           (java.util.concurrent TimeUnit)
           (java.util.logging LogManager)
           (com.yammer.metrics Metrics)
           (com.yammer.metrics.core MetricName)
           (com.ovi.common.metrics HostnameFactory)
           (com.ovi.common.metrics.graphite GraphiteName GraphiteReporterFactory ReporterState)
           (org.slf4j.bridge SLF4JBridgeHandler))
  (:gen-class))

(defn read-file-to-properties [file-name]
  (with-open [^java.io.Reader reader (io/reader file-name)]
    (let [props (java.util.Properties.)]
      (.load props reader)
      (into {} (for [[k v] props] [k v])))))

(defn configure-logging []
  (.reset (LogManager/getLogManager))
  ;Route all java.util.logging log statements to slf4j
  (SLF4JBridgeHandler/install))

(defn build-server-addresses [comma-sep-hosts]
  (map (fn [[h p]] (mc/server-address h (Integer/parseInt p)))
       (map #(cs/split % #":") (cs/split comma-sep-hosts #","))))

(defn configure-mongo-conn-pool []
  (let [^MongoOptions opts (mc/mongo-options
                            :threads-allowed-to-block-for-connection-multiplier 10
                            :connections-per-host (Integer/parseInt (env :mongo-connections-max))
                            :max-wait-time 120000
                            :connect-timeout 30000
                            :socket-timeout 10000
                            :socket-keep-alive false)
        sa (build-server-addresses (env :mongo-hosts))]
    (mc/connect! sa opts)))

(defn configure-mongo-db []
  (mc/use-db! "exploud"))

(defn bootstrap-mongo []
  (mcol/ensure-index "tasks" {:status 1}))

(defn start-graphite-reporting []
  (let [graphite-prefix (new GraphiteName
                             (into-array Object
                                         [(env :environment-name)
                                          (env :service-name)
                                          (HostnameFactory/getHostname)]))]
    (GraphiteReporterFactory/create
     (env :environment-entertainment-graphite-host)
     (Integer/parseInt (env :environment-entertainment-graphite-port))
     graphite-prefix
     (Integer/parseInt (env :service-graphite-post-interval))
     (TimeUnit/valueOf (env :service-graphite-post-unit))
     (ReporterState/valueOf (env :service-graphite-enabled)))))

(defn pick-up-tasks []
  (doseq [task (store/incomplete-tasks)]
    (asg/schedule-track-task (:region task) (:url task) (* 1 60 60))))

(def version
  (delay (if-let [path (.getResource (ClassLoader/getSystemClassLoader) "META-INF/maven/exploud/exploud/pom.properties")]
           ((read-file-to-properties path) "version")
           "localhost")))

(defn setup []
  (web/set-version! @version)
  (configure-mongo-conn-pool)
  (configure-mongo-db)
  (bootstrap-mongo)
  (configure-logging)
  (start-graphite-reporting)
  (pick-up-tasks))

(def server (atom nil))

(defn start-server []
  (run-jetty #'web/app {:port (Integer. (env :service-port))
                        :join? false
                        :stacktraces? (not (Boolean/valueOf (env :service-production)))
                        :auto-reload? (not (Boolean/valueOf (env :service-production)))}))

(defn start []
  (do
    (setup)
    (reset! server (start-server))))

(defn stop [] (if-let [server @server] (.stop server)))

(defn -main [& args]
  (start))
