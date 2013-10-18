(ns exploud.unit.asgard
  (:require [clojure.set :as set]
            [environ.core :refer :all]
            [exploud
             [asgard_new :refer :all]
             [http :as http]
             [store :as store]
             [tyranitar :as tyr]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact-group

 (fact "We should be able to create a complete list of parameters for creating a new ASG from the defaults and the protected params"
       (set/difference
        all-create-new-asg-keys
        (keys (merge default-create-new-asg-params (protected-create-new-asg-params ..app.. ..env.. ..ami.. ..ticket..))))
       => #{})

 (fact "We should be able to create a complete list of parameters for creating the next ASG from the defaults and the protected params"
       (set/difference
        all-create-next-asg-keys
        (keys (merge default-create-next-asg-params (protected-create-next-asg-params ..app. ..env.. ..ami.. ..ticket..))))
       => #{})

 (fact "We don't replace the selected load balancers when not working in a VPC"
       (replace-load-balancer-key {"selectedLoadBalancers" ["load-balancer"]})
       => {"selectedLoadBalancers" ["load-balancer"]})

 (fact "We replace the selected load balancers when working in a VPC"
       (replace-load-balancer-key {"subnetPurpose" "internal"
                                   "selectedLoadBalancers" ["load-balancer"]})
       => {"subnetPurpose" "internal"
           (str "selectedLoadBalancersForVpcId" vpc-id) ["load-balancer"]})

 (fact "We don't replace security group names when not working in a VPC"
       (replace-security-group-names {"selectedSecurityGroups" ["group-one" "group-two"]} ..region..)
       => {"selectedSecurityGroups" ["group-one" "group-two"]})

 (fact "We replace security group names when working in a VPC"
       (replace-security-group-names {"subnetPurpose" "internal"
                                      "selectedSecurityGroups" ["sg-something" "group"]} ..region..)
       => {"subnetPurpose" "internal"
           "selectedSecurityGroups" ["sg-something" "sg-group"]}
       (provided
        (security-groups ..region..)
        => [{:groupId "sg-group"
             :groupName "group"}]))

 (fact "A missing security group throws an exception"
       (replace-security-group-names {"subnetPurpose" "internal"
                                      "selectedSecurityGroups" ["sg-something" "group"]} ..region..)
       => (throws ExceptionInfo "Unknown security group name")
       (provided
        (security-groups ..region..)
        => []))

 (fact "Preparing params works through all expected transformations"
       (prepare-params ..original.. ..region..)
       => ..sg-replaced..
       (provided
        (replace-load-balancer-key ..original..)
        => ..lb-replaced..
        (replace-security-group-names ..lb-replaced.. ..region..)
        => ..sg-replaced..))

 (fact "Exploding params creates a list which replaces maps where the value is a collection with multiple parameters of the same name"
       (explode-params {"some-parameter" ["hello" "world"]
                        "other-thing" "one-value"})
       => [["some-parameter" "hello"]
           ["some-parameter" "world"]
           ["other-thing" "one-value"]])

 (fact "We can retrieve the details about an Auto Scaling Group from Asgard"
       (auto-scaling-group "region" "asg-name")
       => {:name "the-name"}
       (provided
        (http/simple-get
         "http://asgard:8080/region/autoScaling/show/asg-name.json")
        => {:status 200
            :body "{\"name\":\"the-name\"}"}))

 (fact "A missing ASG comes back with nil"
       (auto-scaling-group "region" "asg-name")
       => nil
       (provided
        (http/simple-get
         "http://asgard:8080/region/autoScaling/show/asg-name.json")
        => {:status 404}))

 (fact "We can get the last ASG for an application"
       (last-auto-scaling-group "region" "application-environment")
       => {:autoScalingGroupName "application-environment-v023"}
       (provided
        ( http/simple-get
          "http://asgard:8080/region/cluster/show/application-environment.json")
        => {:status 200
            :body "[{\"autoScalingGroupName\":\"application-environment-v09\"},{\"autoScalingGroupName\":\"application-environment-v023\"}]"}))

 (against-background
  [(auto-scaling-group "region" ..asg..)
   => {}
   (http/simple-post
    "http://asgard:8080/region/cluster/index"
    {:form-params {"_action_resize" ""
                   "minAndMaxSize" ..size..
                   "name" ..asg..
                   "ticket" ..ticket..}})
   => {:status 302
       :headers {"location" "task-url"}}
   (track-until-completed ..ticket.. {:id ..task-id.. :url "task-url.json"} 3600)
   => ..track-result..]

  (fact "Resizing ASG returns whatever was returned by `track-until-completed`."
        (resize-asg "region" ..asg.. ..ticket.. {:id ..task-id..} ..size..)
        => ..track-result..)

  (fact "Non-302 response when resizing ASG throws exception"
        (resize-asg "region" ..asg.. ..ticket.. {:id ..task-id..} ..size..)
        => (throws ExceptionInfo "Unexpected status while resizing ASG")
        (provided
         (http/simple-post
          "http://asgard:8080/region/cluster/index"
          {:form-params {"_action_resize" ""
                         "minAndMaxSize" ..size..
                         "name" ..asg..
                         "ticket" ..ticket..}})
         => {:status 500}))

  (fact "Missing ASG when resizing throws exception"
        (resize-asg "region" ..asg.. ..ticket.. {:id ..task-id..} ..size..)
        => (throws ExceptionInfo "Auto Scaling Group does not exist.")
        (provided
         (auto-scaling-group "region" ..asg..)
         => nil)))

 (fact "We can tell whether a task is finished"
       (finished? {:status "completed"})
       => true
       (finished? {:status "failed"})
       => true
       (finished? {:status "terminated"})
       => true
       (finished? {:status "running"})
       => false)

 (fact "Getting a task does log transformations"
       (first (:log (task-by-url ..task-url..)))
       => (just {:date "2013-10-11T18:25:23Z"
                 :message "Completed in 1s."})
       (provided
        (http/simple-get ..task-url..)
        => {:status 200
            :body "{\"log\":[\"2013-10-11_18:25:23 Completed in 1s.\"]}"}))

 (fact "Getting a task does updateTime transformations"
       (:updateTime (task-by-url ..task-url..))
       => "2013-10-11T14:20:42Z"
       (provided
        (http/simple-get ..task-url..)
        => {:status 200
            :body "{\"updateTime\":\"2013-10-11 14:20:42 UTC\"}"}))

 (fact "Task tracking works for the happy path"
       (track-task "ticket-id" {:url "task-url"} 3600)
       => nil
       (provided
        (task-by-url "task-url")
        => {:status "running"}
        (store/store-task {:status "running" :url "task-url"})
        => nil
        (track-until-completed "ticket-id" {:url "task-url"} 3599)
        => nil))

 (fact "Task tracking sad path attempts to reschedule for :http problem"
       (track-task "ticket-id" {:url "task-url"} 3600)
       => nil
       (provided
        (task-by-url "task-url")
        =throws=> (ex-info "Oh god no!" {:class :http})
        (track-until-completed "ticket-id" {:url "task-url"} 3599)
        => nil))

 (fact "Task tracking sad path attempts to reschedule for :store problem"
       (track-task "ticket-id" {:url "task-url"} 3600)
       => nil
       (provided
        (task-by-url "task-url")
        =throws=> (ex-info "Oh god no!" {:class :store})
        (track-until-completed "ticket-id" {:url "task-url"} 3599)
        => nil))

 (fact "Task tracking sad path blows up for other problem"
       (track-task "ticket-id" {:url "task-url"} 3600)
       => (throws ExceptionInfo "Oh god no!")
       (provided
        (task-by-url "task-url")
        =throws=> (ex-info "Oh god no!" {:class :other})))

 (against-background
  [(auto-scaling-group "region" ..asg..)
   => {}
   (http/simple-post
    "http://asgard:8080/region/cluster/index"
    {:form-params {"_action_delete" ""
                   "name" ..asg..
                   "ticket" ..ticket..}})
   => {:status 302
       :headers {"location" "task-url"}}
   (track-until-completed ..ticket.. {:id ..task-id.. :url "task-url.json"} 3600)
   => ..track-result..]

  (fact "Deleting ASG returns whatever was returned by `track-until-completed`."
        (delete-asg "region" ..asg.. ..ticket.. {:id ..task-id..})
        => ..track-result..)

  (fact "Non-302 response when deleting ASG throws exception"
        (delete-asg "region" ..asg.. ..ticket.. ..task..)
        => (throws ExceptionInfo "Unexpected status while deleting ASG")
        (provided
         (http/simple-post
          "http://asgard:8080/region/cluster/index"
          {:form-params {"_action_delete" ""
                         "name" ..asg..
                         "ticket" ..ticket..}})
         => {:status 500}))

  (fact "Missing ASG when deleting throws exception"
        (delete-asg "region" ..asg.. ..ticket.. ..task..)
        => (throws ExceptionInfo "Auto Scaling Group does not exist.")
        (provided
         (auto-scaling-group "region" ..asg..)
         => nil)))

 (against-background
  [(auto-scaling-group "region" ..asg..) => {}
   (http/simple-post
    "http://asgard:8080/region/cluster/index"
    {:form-params {"_action_activate" ""
                   "name" ..asg..
                   "ticket" ..ticket..}})
   => {:status 302
       :headers {"location" "task-url"}}
   (track-until-completed ..ticket.. {:id ..task-id.. :url "task-url.json"} 3600) => ..track-result..]

  (fact "Enabling ASG returns whatever was returned by `track-until-completed`."
        (enable-asg "region" ..asg.. ..ticket.. {:id ..task-id..})
        => ..track-result..)

  (fact "Non-302 response when enabling ASG throws exception"
        (enable-asg "region" ..asg.. ..ticket.. ..task..)
        => (throws ExceptionInfo "Unexpected status while enabling ASG")
        (provided
         (http/simple-post
          "http://asgard:8080/region/cluster/index"
          {:form-params {"_action_activate" ""
                         "name" ..asg..
                         "ticket" ..ticket..}})
         => {:status 500}))

  (fact "Missing ASG when enabling throws exception"
        (enable-asg "region" ..asg.. ..ticket.. ..task..)
        => (throws ExceptionInfo "Auto Scaling Group does not exist.")
        (provided
         (auto-scaling-group "region" ..asg..)
         => nil)))

 (against-background
  [(auto-scaling-group "region" ..asg..) => {}
   (http/simple-post
    "http://asgard:8080/region/cluster/index"
    {:form-params {"_action_deactivate" ""
                   "name" ..asg..
                   "ticket" ..ticket..}})
   => {:status 302
       :headers {"location" "task-url"}}
   (track-until-completed ..ticket.. {:id ..task-id.. :url "task-url.json"} 3600) => ..track-result..]

  (fact "Disabling ASG returns whatever was returned by `track-until-completed`."
        (disable-asg "region" ..asg.. ..ticket.. {:id ..task-id..})
        => ..track-result..)

  (fact "Non-302 response when disabling ASG throws exception"
        (disable-asg "region" ..asg.. ..ticket.. ..task..)
        => (throws ExceptionInfo "Unexpected status while disabling ASG")
        (provided
         (http/simple-post
          "http://asgard:8080/region/cluster/index"
          {:form-params {"_action_deactivate" ""
                         "name" ..asg..
                         "ticket" ..ticket..}})
         => {:status 500}))

  (fact "Missing ASG when disabling throws exception"
        (disable-asg "region" ..asg.. ..ticket.. ..task..)
        => (throws ExceptionInfo "Auto Scaling Group does not exist.")
        (provided
         (auto-scaling-group "region" ..asg..)
         => nil)))

 (fact "We can find the new ASG name from the `Location` header"
       (extract-new-asg-name "http://asgard/eu-west-1/autoScaling/show/my-new-asg")
       => "my-new-asg")

 (against-background
  [(tyr/deployment-params ..environment.. ..application.. ..hash..)
   => {"from" "user"}
   (create-asgard-params "region" "application" "environment" ..ami.. ..hash.. ..ticket..)
   => ..asgard-params..
   (explode-params ..asgard-params..)
   => ..exploded-params..]

  (fact "Create new ASG happy path"
        (create-new-asg "region" "application" "environment" ..ami.. ..hash.. ..ticket.. {:id ..task-id..})
        => "new-asg-name"
        (provided
         (http/simple-post
          "http://asgard:8080/region/autoScaling/save"
          {:form-params ..exploded-params..})
         => {:status 302
             :headers {"location" "http://asgard:8080/region/autoScaling/show/new-asg-name"}}
         (tasks "region")
         => [{:name "Something we don't care about"}
             {:id 420 :name "Create Auto Scaling Group 'new-asg-name'"}]
         (track-until-completed ..ticket.. {:id ..task-id..
                                            :url "http://asgard:8080/region/task/show/420.json"} 3600)
         => ..track-result..))

  (fact "Non-302 response when creating next ASG throws exception"
        (create-new-asg "region" "application" "environment" ..ami.. ..hash.. ..ticket.. ..task..)
        => (throws ExceptionInfo "Unexpected status while creating new ASG")
        (provided
         (http/simple-post
          "http://asgard:8080/region/autoScaling/save"
          {:form-params ..exploded-params..})
         => {:status 500})))

 (against-background
  [(tyr/deployment-params "environment" "application" ..hash..)
   => {"from" "user"}
   (create-asgard-params "region" "application" "environment" ..ami.. ..hash.. ..ticket..)
   => ..asgard-params..
   (explode-params ..asgard-params..)
   => ..exploded-params..]

  (fact "Create next ASG happy path"
        (create-next-asg "region" "application" "environment" ..ami.. ..hash.. ..ticket.. {:id ..task-id..})
        => ..track-result..
        (provided
         (http/simple-post
          "http://asgard:8080/region/cluster/createNextGroup"
          {:form-params ..exploded-params..})
         => {:status 302
             :headers {"location" "http://asgard:8080/region/task/show/426"}}
         (track-until-completed ..ticket.. {:id ..task-id..
                                            :url "http://asgard:8080/region/task/show/426.json"} 3600)
         => ..track-result..))

  (fact "Non-302 response when creating next ASG throws exception"
        (create-next-asg "region" "application" "environment" ..ami.. ..hash.. ..ticket.. ..task..)
        => (throws ExceptionInfo "Unexpected status while creating next ASG")
        (provided
         (http/simple-post
          "http://asgard:8080/region/cluster/createNextGroup"
          {:form-params ..exploded-params..})
         => {:status 500}))))
