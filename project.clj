(defproject exploud "0.22-SNAPSHOT"
  :description "Exploud service"
  :url "http://wikis.in.nokia.com/NokiaMusicArchitecture/Exploud"

                 [bouncer "0.3.0-alpha1"]
                 [ch.qos.logback/logback-classic "1.0.13"]
                 [cheshire "5.2.0"]
                 [clj-http "0.7.7"]
                 [clj-time "0.6.0"]
                 [com.draines/postal "1.11.1"]
                 [com.novemberain/monger "1.6.0"]
                 [com.ovi.common.logging/logback-appender "0.0.45"]
                 [com.ovi.common.metrics/metrics-graphite "2.1.22"]
                 [com.yammer.metrics/metrics-logback "2.2.0"]
                 [compojure "1.1.6" :exclusions [javax.servlet/servlet-api]]
                 [dire "0.5.1"]
                 [environ "0.4.0"]
                 [metrics-clojure "1.0.1"]
                 [metrics-clojure-ring "1.0.1"]
                 [nokia/ring-utils "1.2.1"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.3"]
                 [org.clojure/data.xml "0.0.7"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.eclipse.jetty/jetty-server "8.1.14.v20131031"]
                 [org.slf4j/jcl-over-slf4j "1.7.5"]
                 [org.slf4j/jul-to-slf4j "1.7.5"]
                 [org.slf4j/log4j-over-slf4j "1.7.5"]
                 [org.slf4j/slf4j-api "1.7.5"]
                 [overtone/at-at "1.2.0"]
                 [ring-json-params "0.1.3"]
                 [ring-middleware-format "0.3.1"]
                 [ring/ring-jetty-adapter "1.2.1" :exclusions [org.eclipse.jetty/jetty-server]]]

  :exclusions [commons-logging
               log4j]

  :profiles {:dev {:dependencies [[midje "1.6.0"]]
                   :plugins [[lein-rpm "0.0.5"]
                             [lein-midje "3.1.3"]
                             [jonase/kibit "0.0.8"]]}}

  :plugins [[lein-cloverage "1.0.2"]
            [lein-embongo "0.2.1"]
            [lein-marginalia "0.7.1"]
            [lein-ring "0.8.8"]
            [lein-environ "0.4.0"]
            [lein-release "1.0.73"]]

  :env {:environment-name "Dev"
        :service-name "exploud"
        :service-port "8080"
        :environment-entertainment-graphite-host "graphite.brislabs.com"
        :environment-entertainment-graphite-port "8080"
        :service-graphite-post-interval "1"
        :service-graphite-post-unit "MINUTES"
        :service-graphite-enabled "ENABLED"
        :service-production "false"
        :service-dev-asgard-url "http://dev.asgard:8080"
        :service-prod-asgard-url "http://prod.asgard:8080"
        :service-onix-url "http://onix:8080"
        :service-shuppet-url "http://shuppet:8080"
        :service-tyranitar-url "http://tyranitar:8080"
        :service-smtp-host ""
        :service-mail-from "exploud@brislabs.com"
        :service-mail-to "I_EXT_ENT_DEPLOYMENT_COMMS@nokia.com"
        :mongo-hosts "localhost:27017"
        :mongo-connections-max "50"
        :service-dev-vpc-id "vpc-dev"
        :service-prod-vpc-id "vpc-prod"}

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
                   {:directory "/usr/local/deployment/exploud/bin"
                    :filemode "744"
                    :sources {:source [{:location "scripts/dmt"}]}}
                   {:directory "/etc/rc.d/init.d"
                    :filemode "744"
                    :username "exploud"
                    :groupname "exploud"
                    :sources {:source [{:location "scripts/service/exploud"}]}}]}

  :main exploud.setup)
