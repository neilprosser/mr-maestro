(defproject maestro "0.148-SNAPSHOT"
  :description "Maestro service"
  :license  "https://github.com/mixradio/mr-maestro/blob/master/LICENSE"

  :dependencies [[amazonica "0.3.29" :exclusions [com.fasterxml.jackson.core/jackson-annotations]]
                 [bouncer "0.3.3"]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [cheshire "5.5.0"]
                 [clj-http "2.0.0"]
                 [clj-time "0.10.0"]
                 [clojurewerkz/elastisch "2.1.0"]
                 [com.cemerick/url "0.1.1"]
                 [com.ninjakoala/aws-instance-metadata "1.0.0"]
                 [com.ninjakoala/monotony "1.0"]
                 [com.ninjakoala/ttlr "1.0.1"]
                 [com.taoensso/carmine "2.11.1"]
                 [compojure "1.4.0"]
                 [dire "0.5.3"]
                 [environ "1.0.0"]
                 [io.clj/logging "0.8.1"]
                 [joda-time "2.8.1"]
                 [mixradio/graphite-filter "1.0.0"]
                 [mixradio/instrumented-ring-jetty-adapter "1.0.4"]
                 [mixradio/radix "1.0.10"]
                 [net.logstash.logback/logstash-logback-encoder "4.4"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.tobereplaced/lettercase "1.0.0"]
                 [ring-middleware-format "0.5.0"]]

  :exclusions [commons-logging
               joda-time
               log4j
               org.clojure/clojure]

  :profiles {:dev {:dependencies [[midje "1.7.0"]]
                   :plugins [[lein-kibit "0.0.8"]
                             [lein-midje "3.1.3"]
                             [lein-rpm "0.0.5"]]}}

  :plugins [[lein-cloverage "1.0.2"]
            [lein-environ "1.0.0"]
            [lein-marginalia "0.8.0"]
            [lein-release "1.0.5"]
            [lein-ring "0.9.0"]]

  :env {:aws-key-name "maestro"
        :aws-required-security-groups "maestro-healthcheck"
        :aws-role-name "maestro"
        :disable-caching true
        :elasticsearch-index-name "maestro"
        :elasticsearch-url "http://localhost:9200"
        :environment-name "dev"
        :graphite-enabled false
        :graphite-host "carbon"
        :graphite-port 2003
        :graphite-post-interval-seconds 60
        :hubot-deployments-room "deployments"
        :hubot-url "http://hubot"
        :logging-consolethreshold "off"
        :logging-filethreshold "info"
        :logging-level "info"
        :logging-path "/tmp"
        :logging-stashthreshold "warn"
        :numel-poke-baseurl "http://numelpoke"
        :numel-prod-baseurl "http://numelprod"
        :lister-baseurl "http://lister"
        :pedantic-baseurl "http://pedantic"
        :production false
        :redis-host "localhost"
        :redis-key-prefix "maestro"
        :redis-port 6379
        :redis-queue-threads 1
        :requestlog-enabled false
        :requestlog-retainhours 24
        :service-dev-vpc-id "vpc-dev"
        :service-jvmargs ""
        :service-name "maestro"
        :service-port 8080
        :shutdown-timeout-millis 5000
        :start-timeout-seconds 120
        :threads 254
        :tyrant-baseurl "http://tyrant"
        :ui-baseurl "http://maestro-ui"}

  :clean-targets ^{:protect false} [:target-path "docs"]

  :lein-release {:deploy-via :shell
                 :shell ["lein" "do" "clean," "uberjar," "pom," "rpm"]}

  :ring {:handler maestro.web/app
         :main maestro.setup
         :port ~(Integer/valueOf (get (System/getenv) "SERVICE_PORT" "8080"))
         :init maestro.setup/setup
         :browser-uri "/healthcheck"
         :nrepl {:start? true}}

  :uberjar-name "maestro.jar"

  :eastwood {:namespaces [:source-paths]}

  :rpm {:name "maestro"
        :summary "RPM for Maestro service"
        :copyright "MixRadio 2014"
        :preinstall {:scriptFile "scripts/rpm/preinstall.sh"}
        :postinstall {:scriptFile "scripts/rpm/postinstall.sh"}
        :preremove {:scriptFile "scripts/rpm/preremove.sh"}
        :postremove {:scriptFile "scripts/rpm/postremove.sh"}
        :requires ["jdk >= 2000:1.7.0_55-fcs"]
        :mappings [{:directory "/usr/local/maestro"
                    :filemode "444"
                    :username "maestro"
                    :groupname "maestro"
                    :sources {:source [{:location "target/maestro.jar"}]}}
                   {:directory "/usr/local/maestro/bin"
                    :filemode "744"
                    :username "maestro"
                    :groupname "maestro"
                    :sources {:source [{:location "scripts/bin"}]}}
                   {:directory "/etc/rc.d/init.d"
                    :filemode "755"
                    :sources {:source [{:location "scripts/service/maestro"
                                        :destination "maestro"}]}}]}

  :main maestro.setup)
