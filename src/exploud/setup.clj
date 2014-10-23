(ns exploud.setup
  "## Setting up our application"
  (:require [cheshire.generate :as json]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [exploud
             [elasticsearch :as elasticsearch]
             [environments :as environments]
             [messages :as messages]
             [redis :as redis]
             [web :as web]]
            [mixradio.instrumented-jetty :refer [run-jetty]]
            [ninjakoala.monotony :refer [redirect-logging]]
            [radix.setup :as setup])
  (:import (org.joda.time DateTime DateTimeZone))
  (:gen-class))

(defn configure-joda
  "Configures Joda Time to use UTC as the default timezone (in case someone
   hasn't included it in the JVM args."
  []
  (json/add-encoder DateTime (fn [dt jg] (.writeString jg (str dt))))
  (DateTimeZone/setDefault DateTimeZone/UTC))

(defonce server
  (atom nil))

(defn configure-server
  "Configures the server."
  [server]
  (doto server
    (.setStopAtShutdown true)
    (.setGracefulShutdown setup/shutdown-timeout)))

(defn start-server
  "Starts the server."
  []
  (run-jetty #'web/app {:port setup/service-port
                        :join? false
                        :stacktraces? (not setup/production?)
                        :auto-reload? (not setup/production?)
                        :configurator configure-server
                        :send-server-version false}))

(defn start
  "Sets up our application and starts the server."
  []
  (redirect-logging)
  (configure-joda)
  (setup/configure-logging)
  (setup/start-graphite-reporting {:graphite-prefix (str/join "." [(env :environment-name) (env :service-name) (env :box-id setup/hostname)])})
  (redis/init messages/handler)
  (elasticsearch/init)
  (environments/init)
  (reset! server (start-server)))

(defn stop
  "Stops the server."
  []
  (when-let [s @server]
    (.stop s)
    (reset! server nil))
  (shutdown-agents))

(defn -main
  "The entry point for the application."
  [& args]
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop))
  (start))
