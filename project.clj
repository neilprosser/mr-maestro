(defproject exploud "0.101-SNAPSHOT"
  :description "Exploud service"
  :url "http://github.brislabs.com/cloud-tooling/exploud/wiki"

  :dependencies [[amazonica "0.2.29" :exclusions [com.fasterxml.jackson.core/jackson-annotations]]
                 [bouncer "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [cheshire "5.3.1"]
                 [clj-http "1.0.1"]
                 [clj-time "0.8.0"]
                 [clojurewerkz/elastisch "2.0.0"]
                 [com.cemerick/url "0.1.1"]
                 [com.draines/postal "1.11.2"]
                 [com.github.frankiesardo/linked "0.1.0"]
                 [com.ninjakoala/monotony "1.0"]
                 [com.taoensso/carmine "2.7.1"]
                 [compojure "1.2.1"]
                 [dire "0.5.2"]
                 [environ "1.0.0"]
                 [io.clj/logging "0.8.1"]
                 [joda-time "2.5"]
                 [mixradio/graphite-filter "1.0.0"]
                 [mixradio/instrumented-ring-jetty-adapter "1.0.4"]
                 [mixradio/radix "1.0.9"]
                 [net.logstash.logback/logstash-logback-encoder "3.4"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.tobereplaced/lettercase "1.0.0"]
                 [overtone/at-at "1.2.0"]
                 [ring-middleware-format "0.4.0"]]

  :exclusions [commons-logging
               joda-time
               log4j
               org.clojure/clojure]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[lein-kibit "0.0.8"]
                             [lein-midje "3.1.3"]
                             [lein-rpm "0.0.5"]]}}

  :plugins [[lein-cloverage "1.0.2"]
            [lein-environ "1.0.0"]
            [lein-marginalia "0.8.0"]
            [lein-release "1.0.5"]
            [lein-ring "0.8.13"]]

  :env {:aws-dev-account-id "dev-account-id"
        :aws-dev-autoscaling-topic-arn "dev-autoscaling-topic-arn"
        :aws-prod-account-id "prod-account-id"
        :aws-prod-autoscaling-topic-arn "prod-autoscaling-topic-arn"
        :aws-prod-role-arn "prod-role-arn"
        :disable-caching true
        :elasticsearch-url "http://localhost:9200"
        :environment-name "dev"
        :graphite-enabled false
        :graphite-host "carbon.brislabs.com"
        :graphite-port 2003
        :graphite-post-interval-seconds 60
        :hubot-url "http://hubot:8080"
        :logging-consolethreshold "off"
        :logging-filethreshold "info"
        :logging-level "info"
        :logging-path "/tmp"
        :logging-stashthreshold "warn"
        :mail-from-address "exploud@brislabs.com"
        :mail-smtp-host ""
        :mail-to-address "I_EXT_ENT_DEPLOYMENT_COMMS@nokia.com"
        :numel-poke-baseurl "http://numelpoke:8080"
        :numel-prod-baseurl "http://numelprod:8080"
        :onix-baseurl "http://onix"
        :prod-vpc-id "vpc-prod"
        :production false
        :redis-host "localhost"
        :redis-port 6379
        :redis-queue-threads 1
        :requestlog-enabled false
        :requestlog-retainhours 24
        :service-dev-vpc-id "vpc-dev"
        :service-jvmargs ""
        :service-name "exploud"
        :service-port 8080
        :shuppet-baseurl "http://shuppet:8080"
        :shutdown-timeout-millis 5000
        :start-timeout-seconds 120
        :threads 254
        :tyranitar-baseurl "http://tyranitar"}

  :clean-targets ^{:protect false} [:target-path "docs"]

  :lein-release {:deploy-via :shell
                 :shell ["lein" "do" "clean," "uberjar," "pom," "rpm"]}

  :ring {:handler exploud.web/app
         :main exploud.setup
         :port ~(Integer/valueOf (get (System/getenv) "SERVICE_PORT" "8080"))
         :init exploud.setup/setup
         :browser-uri "/healthcheck"
         :nrepl {:start? true}}

  :uberjar-name "exploud.jar"

  :eastwood {:namespaces [:source-paths]}

  :rpm {:name "exploud"
        :summary "RPM for Exploud service"
        :copyright "MixRadio 2014"
        :preinstall {:scriptFile "scripts/rpm/preinstall.sh"}
        :postinstall {:scriptFile "scripts/rpm/postinstall.sh"}
        :preremove {:scriptFile "scripts/rpm/preremove.sh"}
        :postremove {:scriptFile "scripts/rpm/postremove.sh"}
        :requires ["jdk >= 2000:1.7.0_55-fcs"]
        :mappings [{:directory "/usr/local/exploud"
                    :filemode "444"
                    :username "exploud"
                    :groupname "exploud"
                    :sources {:source [{:location "target/exploud.jar"}]}}
                   {:directory "/usr/local/exploud/bin"
                    :filemode "744"
                    :username "exploud"
                    :groupname "exploud"
                    :sources {:source [{:location "scripts/bin"}]}}
                   {:directory "/etc/rc.d/init.d"
                    :filemode "755"
                    :sources {:source [{:location "scripts/service/exploud"
                                        :destination "exploud"}]}}]}

  :main exploud.setup)
