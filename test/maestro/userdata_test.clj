(ns maestro.userdata-test
  (:require [maestro.userdata :refer :all]
            [midje.sweet :refer :all]))

(fact "that creating user-data works"
      (create-user-data {:application "application"
                         :environment "environment"
                         :new-state {:tyranitar {:application-properties {:service.port 8080
                                                                          :path.with.dollar "abc$xyz"}
                                                 :deployment-params {:subscriptions ["something" "something-else"]}
                                                 :launch-data ["echo business"]}}})
      => "#!/bin/bash
cat > /etc/profile.d/asgard.sh <<EOF
export CLOUD_APP=application
export CLOUD_CLUSTER=application-environment
export CLOUD_STACK=environment
EOF
mkdir -p /etc/sensu/conf.d/subscriptions.d
cat > /etc/sensu/conf.d/subscriptions.d/application.json <<EOF
{\"client\":{\"subscriptions\":[\"something\",\"something-else\"]}}
EOF
cat > /var/encrypted/properties/application.properties <<EOF
path.with.dollar=abc\\$xyz
service.port=8080
EOF
ln -s /var/encrypted/properties/application.properties /etc/application.properties
echo business")

(fact "that creating user-data works when subscriptions are missing"
      (create-user-data {:application "application"
                         :environment "environment"
                         :new-state {:tyranitar {:application-properties {:service.port 8080
                                                                          :path.with.dollar "abc$xyz"}
                                                 :launch-data ["echo business"]}}})
      => "#!/bin/bash
cat > /etc/profile.d/asgard.sh <<EOF
export CLOUD_APP=application
export CLOUD_CLUSTER=application-environment
export CLOUD_STACK=environment
EOF
cat > /var/encrypted/properties/application.properties <<EOF
path.with.dollar=abc\\$xyz
service.port=8080
EOF
ln -s /var/encrypted/properties/application.properties /etc/application.properties
echo business")
