(ns exploud.userdata-test
  (:require [exploud.userdata :refer :all]
            [midje.sweet :refer :all]))

(fact "that creating user-data works"
      (create-user-data {:application "application"
                         :environment "environment"
                         :new-state {:tyranitar {:application-properties {:service.port 8080}
                                                 :launch-data ["echo business"]}}})
      => "#!/bin/bash
cat > /etc/profile.d/asgard.sh <<EOF
export CLOUD_APP=application
export CLOUD_CLUSTER=application-environment
export CLOUD_STACK=environment
EOF
cat > /var/encrypted/properties/application.properties <<EOF
service.port=8080
EOF
ln -s /var/encrypted/properties/application.properties /etc/application.properties
echo business")
