(defproject exploud "0.22-SNAPSHOT"
  :description "Exploud service"
  :url "http://wikis.in.nokia.com/NokiaMusicArchitecture/Exploud"

  :dependencies [[amazonica "0.2.10"]
                 [bouncer "0.3.0"]
                 [ch.qos.logback/logback-classic "1.1.1"]
                 [cheshire "5.3.1"]
                 [clj-http "0.9.0"]
                 [clj-time "0.6.0"]
                 [com.cemerick/url "0.1.1"]
                 [com.draines/postal "1.11.1"]
                 [com.novemberain/monger "1.7.0"]
                 [com.ovi.common.logging/logback-appender "0.0.45"]
                 [com.ovi.common.metrics/metrics-graphite "2.1.23"]
                 [com.yammer.metrics/metrics-logback "2.2.0"]
                 [compojure "1.1.6" :exclusions [javax.servlet/servlet-api]]
                 [dire "0.5.2"]
                 [environ "0.4.0"]
                 [metrics-clojure "1.0.1"]
                 [metrics-clojure-ring "1.0.1"]
                 [nokia/instrumented-ring-jetty-adapter "0.1.8"]
                 [nokia/ring-utils "1.2.1"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.4"]
                 [org.clojure/data.xml "0.0.7"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.eclipse.jetty/jetty-server "8.1.14.v20131031"]
                 [org.flatland/ordered "1.5.2"]
                 [org.slf4j/jcl-over-slf4j "1.7.6"]
                 [org.slf4j/jul-to-slf4j "1.7.6"]
                 [org.slf4j/log4j-over-slf4j "1.7.6"]
                 [org.slf4j/slf4j-api "1.7.6"]
                 [overtone/at-at "1.2.0"]
                 [ring-json-params "0.1.3"]
                 [ring-middleware-format "0.3.2"]]

  :exclusions [commons-logging
               log4j]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[jonase/kibit "0.0.8"]
                             [lein-midje "3.1.3"]
                             [lein-rpm "0.0.5"]]}}

  :plugins [[lein-cloverage "1.0.2"]
            [lein-embongo "0.2.1"]
            [lein-environ "0.4.0"]
            [lein-marginalia "0.7.1"]
            [lein-release "1.0.73"]
            [lein-ring "0.8.10"]]

  :env {:aws-poke-account-id "poke-account-id"
        :aws-poke-autoscaling-topic-arn "poke-autoscaling-topic-arn"
        :aws-prod-account-id "prod-account-id"
        :aws-prod-autoscaling-topic-arn "prod-autoscaling-topic-arn"
        :aws-prod-role-arn "prod-role-arn"
        :environment-entertainment-graphite-host "graphite.brislabs.com"
        :environment-entertainment-graphite-port "8080"
        :environment-name "Dev"
        :mongo-connections-max "50"
        :mongo-hosts "localhost:27017"
        :service-dev-asgard-url "http://dev.asgard:8080"
        :service-dev-vpc-id "vpc-dev"
        :service-graphite-post-interval "1"
        :service-graphite-post-unit "MINUTES"
        :service-graphite-enabled "ENABLED"
        :service-mail-from "exploud@brislabs.com"
        :service-mail-to "I_EXT_ENT_DEPLOYMENT_COMMS@nokia.com"
        :service-name "exploud"
        :service-port "8080"
        :service-onix-url "http://onix:8080"
        :service-prod-asgard-url "http://prod.asgard:8080"
        :service-prod-vpc-id "vpc-prod"
        :service-production "false"
        :service-shuppet-url "http://shuppet:8080"
        :service-smtp-host ""
        :service-tyranitar-url "http://tyranitar:8080"}

  :clean-targets [:target-path "docs"]

  :lein-release {:release-tasks [:clean :uberjar :pom :rpm]
                 :clojars-url "clojars@clojars.brislabs.com:"}

  :embongo {:port ~(Integer. (get (System/getenv) "MONGO_PORT" "27017"))
            :version "2.4.3"}

  :ring {:handler exploud.web/app
         :main exploud.setup
         :port ~(Integer. (get (System/getenv) "SERVICE_PORT" "8080"))
         :init exploud.setup/setup
         :browser-uri "/1.x/status"}

  :repositories {"internal-clojars"
                 "http://clojars.brislabs.com/repo"
                 "rm.brislabs.com"
                 "http://rm.brislabs.com/nexus/content/groups/all-releases"}

  :uberjar-name "exploud.jar"

  :rpm {:name "exploud"
        :summary "RPM for Exploud service"
        :copyright "Nokia 2013"
        :preinstall {:scriptFile "scripts/rpm/preinstall.sh"}
        :postinstall {:scriptFile "scripts/rpm/postinstall.sh"}
        :preremove {:scriptFile "scripts/rpm/preremove.sh"}
        :postremove {:scriptFile "scripts/rpm/postremove.sh"}
        :requires ["jdk >= 2000:1.7.0_25-fcs"]
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
                    :filemode "744"
                    :username "exploud"
                    :groupname "exploud"
                    :sources {:source [{:location "scripts/service/exploud"}]}}]}

  :main exploud.setup)
