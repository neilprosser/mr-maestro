(ns exploud.setup
    (:require [exploud.web :as web]
              [environ.core :refer [env]]
              [clojure.string :as cs :only (split)]
              [clojure.tools.logging :refer (info warn error)]
              [clojure.java.io :as io]
              [monger.core :as mc :only (connect! mongo-options server-address use-db!)]
              [ring.adapter.jetty :refer [run-jetty]])
    (:import (java.lang Integer Throwable)
             (java.util.logging LogManager)
             (com.yammer.metrics Metrics)
             (com.yammer.metrics.core MetricName)
             (com.ovi.common.metrics.graphite GraphiteReporterFactory GraphiteName ReporterState)
             (com.ovi.common.metrics HostnameFactory)
             (org.slf4j.bridge SLF4JBridgeHandler)
             (java.util.concurrent TimeUnit))
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

(def version
  (delay (if-let [path (.getResource (ClassLoader/getSystemClassLoader) "META-INF/maven/exploud/exploud/pom.properties")]
           ((read-file-to-properties path) "version")
           "localhost")))

(defn setup []
  (web/set-version! @version)
  (configure-mongo-conn-pool)
  (configure-mongo-db)
  (configure-logging)
  (start-graphite-reporting))

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
