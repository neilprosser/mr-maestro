(ns exploud.asgard_test
  (:require [clj-time.format :as fmt]
            [clojure.set :as set]
            [environ.core :refer :all]
            [exploud
             [asgard :refer :all]
             [http :as http]
             [store :as store]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "We should be able to create a complete list of parameters for creating a new ASG from the defaults and the protected params"
      (set/difference
       all-create-new-asg-keys
       (keys (merge default-create-new-asg-parameters (protected-create-new-asg-parameters ..app.. ..env.. ..ami.. ..ticket..))))
      => #{})

(fact "We should be able to create a complete list of parameters for creating the next ASG from the defaults and the protected params"
      (set/difference
       all-create-next-asg-keys
       (keys (merge default-create-next-asg-parameters (protected-create-next-asg-parameters ..app. ..env.. ..ami.. ..ticket..))))
      => #{})

(fact "that remove-nil-values works"
      (remove-nil-values {:something nil :another "thing"})
      => {:another "thing"})

(fact "We don't replace the selected load balancers when not working in a VPC"
      (replace-load-balancer-key {:selectedLoadBalancers ["load-balancer"]})
      => {:selectedLoadBalancers ["load-balancer"]})

(fact "We replace the selected load balancers when working in a VPC"
      (replace-load-balancer-key {:subnetPurpose "internal"
                                  :selectedLoadBalancers ["load-balancer"]})
      => {:subnetPurpose "internal"
          (keyword (str "selectedLoadBalancersForVpcId" vpc-id)) ["load-balancer"]})

(fact "We gracefully handle no load balancer when working in a VPC"
      (replace-load-balancer-key {:subnetPurpose "internal"})
      => {:subnetPurpose "internal"})

(fact "We gracefully handle a single load balancer specified as a string"
      (replace-load-balancer-key {:subnetPurpose "internal"
                                  :selectedLoadBalancers "load-balancer"})
      => {:subnetPurpose "internal"
          (keyword (str "selectedLoadBalancersForVpcId" vpc-id)) "load-balancer"})

(fact "We don't replace security group names when not working in a VPC"
      (replace-security-group-names {:selectedSecurityGroups ["group-one" "group-two"]} ..region..)
      => {:selectedSecurityGroups ["group-one" "group-two"]})

(fact "We replace security group names when working in a VPC"
      (replace-security-group-names {:subnetPurpose "internal"
                                     :selectedSecurityGroups ["sg-something" "group"]} ..region..)
      => {:subnetPurpose "internal"
          :selectedSecurityGroups ["sg-something" "sg-group"]}
      (provided
       (security-groups ..region..)
       => [{:groupId "sg-group"
            :groupName "group"}]))

(fact "A missing security group throws an exception"
      (replace-security-group-names {:subnetPurpose "internal"
                                     :selectedSecurityGroups ["sg-something" "group"]} ..region..)
      => (throws ExceptionInfo "Unknown security group name")
      (provided
       (security-groups ..region..)
       => []))

(fact "We gracefully handle a security group name specified as a single string"
      (replace-security-group-names {:subnetPurpose "internal"
                                     :selectedSecurityGroups "group"} ..region..)
      => {:subnetPurpose "internal"
          :selectedSecurityGroups ["sg-group"]}
      (provided
       (security-groups ..region..)
       => [{:groupId "sg-group"
            :groupName "group"}]))

(fact "that we add the whole `:selectedSecurityGroups` key and exploud security group when no groups are given"
      (add-exploud-security-group {} ..region..)
      => {:selectedSecurityGroups ["sg-group"]}
      (provided
       (security-groups ..region..)
       => [{:groupId "sg-group"
            :groupName "exploud-healthcheck"}]))

(fact "that we add the exploud security group to any existing ones"
      (add-exploud-security-group {:selectedSecurityGroups ["sg-something"]} ..region..)
      => {:selectedSecurityGroups ["sg-something" "sg-exploud"]}
      (provided
       (security-groups ..region..)
       => [{:groupId "sg-exploud"
            :groupName "exploud-healthcheck"}]))

(fact "that we replace individual zones with the region + zone when only one zones is given"
      (add-region-to-zones {:selectedZones "a"} "region")
      => {:selectedZones ["regiona"]})

(fact "that we replace individual zones with region + zone when multiple zones are given"
      (add-region-to-zones {:selectedZones ["a" "b"]} "region")
      => {:selectedZones ["regiona" "regionb"]})

(fact "that we gracefully handle there being no zones specified"
      (add-region-to-zones {:something "irrelevant"} "region")
      => {:something "irrelevant"})

(fact "Preparing params works through all expected transformations"
      (prepare-parameters ..original.. ..region..)
      => ..zones-replaced..
      (provided
       (remove-nil-values ..original..)
       => ..nil-replaced..
       (replace-load-balancer-key ..nil-replaced..)
       => ..lb-replaced..
       (replace-security-group-names ..lb-replaced.. ..region..)
       => ..sg-replaced..
       (add-exploud-security-group ..sg-replaced.. ..region..)
       => ..sg-added..
       (add-region-to-zones ..sg-added.. ..region..)
       => ..zones-replaced..))

(fact "Exploding params creates a list which replaces maps where the value is a collection with multiple parameters of the same name"
      (explode-parameters {:some-parameter ["hello" "world"]
                           :other-thing "one-value"})
      => [["some-parameter" "hello"]
          ["some-parameter" "world"]
          ["other-thing" "one-value"]])

(fact "that munging a task handles a task which doesn't need changing"
      (munge-task {:something "tasky"})
      => {:something "tasky"})

(fact "that munging a task handles splits log messages"
      (munge-task {:log [..log-1.. ..log-2..]})
      => {:log [..split-log-1.. ..split-log-2..]}
      (provided
       (split-log-message ..log-1..)
       => ..split-log-1..
       (split-log-message ..log-2..)
       => ..split-log-2..))

(fact "that munging a task updates the updateTime"
      (munge-task {:updateTime ..update-time..})
      => {:updateTime ..corrected-update-time..}
      (provided
       (correct-date-time ..update-time..)
       => ..corrected-update-time..))

(fact "that munging a task does all alterations"
      (munge-task {:log [..log..] :updateTime ..update-time..})
      => {:log [..split-log..] :updateTime ..corrected-update-time..}
      (provided
       (split-log-message ..log..)
       => ..split-log..
       (correct-date-time ..update-time..)
       => ..corrected-update-time..))

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

(fact "that we can retrieve an instance from Asgard"
      (instance "region" "id")
      => {:id "id"
          :something "this"}
      (provided
       (http/simple-get
        "http://asgard:8080/region/instance/show/id.json")
       => {:status 200
           :body "{\"id\":\"id\",\"something\":\"this\"}"}))

(fact "that a missing instance comes back with nil"
      (instance "region" "id")
      => nil
      (provided
       (http/simple-get
        "http://asgard:8080/region/instance/show/id.json")
       => {:status 404}))

(fact "that we can retrieve a load-balancer form Asgard"
      (load-balancer "region" "elb")
      => {:id "id"
          :something "this"}
      (provided
       (http/simple-get
        "http://asgard:8080/region/loadBalancer/show/elb.json")
       => {:status 200
           :body "{\"id\":\"id\",\"something\":\"this\"}"}))

(fact "that a missing load-balancer comes back with nil"
      (load-balancer "region" "elb")
      => nil
      (provided
       (http/simple-get
        "http://asgard:8080/region/loadBalancer/show/elb.json")
       => {:status 404}))

(fact "that we can retrieve the instances in an ASG"
      (instances-in-asg "region" "asg")
      => [..instance-1.. ..instance-2..]
      (provided
       (auto-scaling-group "region" "asg")
       => {:group {:instances [{:instanceId "i-1"}
                               {:instanceId "i-2"}]}}
       (instance "region" "i-1")
       => ..instance-1..
       (instance "region" "i-2")
       => ..instance-2..))

(fact "that a missing ASG gives nil when getting instances from a non-existent ASG"
      (instances-in-asg "region" "asg")
      => nil
      (provided
       (auto-scaling-group "region" "asg")
       => nil))

(fact "We can get the list of applications in Asgard"
      (applications)
      => {:applications [{:name "something"}]}
      (provided
       (http/simple-get
        "http://asgard:8080/application/list.json")
       => {:status 200
           :body "{\"applications\":[{\"name\":\"something\"}]}"}))

(fact "We can retrieve the security groups within a region"
      (security-groups "region")
      => [{:name "group"}]
      (provided
       (http/simple-get
        "http://asgard:8080/region/security/list.json")
       => {:status 200
           :body "{\"securityGroups\":[{\"name\":\"group\"}]}"}))

(fact "We can retrieve tasks from Asgard"
      (tasks)
      => [{:id "task-1"}
          {:id "task-2"}]
      (provided
       (http/simple-get
        "http://asgard:8080/task/list.json")
       => {:status 200
           :body "{\"runningTaskList\":[{\"id\":\"task-1\"}],
                   \"completedTaskList\":[{\"id\":\"task-2\"}]}"}))

(fact "We can upsert an application"
      (upsert-application "application" {:description "description"
                                         :email "email"
                                         :owner "owner"})
      => ..response..
      (provided
       (http/simple-post
        "http://asgard:8080/application/index"
        {:form-params {:description "description"
                       :email "email"
                       :monitorBucketType "application"
                       :name "application"
                       :owner "owner"
                       :ticket ""
                       :type "Web Service"
                       :_action_update ""}
         :follow-redirects false})
       => ..response..))

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
  (explode-parameters {:_action_resize ""
                       :minAndMaxSize ..size..
                       :name ..asg..
                       :ticket ..ticket..})
  => ..asgard-params..
  (http/simple-post
   "http://asgard:8080/region/cluster/index"
   {:form-params ..asgard-params..})
  => {:status 302
      :headers {"location" "task-url"}}
  (track-until-completed ..ticket.. {:id ..task-id..
                                     :url "task-url.json"
                                     :asgardParameters {:_action_resize ""
                                                        :minAndMaxSize ..size..
                                                        :name ..asg..
                                                        :ticket ..ticket..}} 3600 ..completed.. ..timed-out..)
  => ..track-result..]

 (fact "Resizing ASG returns whatever was returned by `track-until-completed`."
       (resize-asg "region" ..asg.. ..ticket.. {:id ..task-id..} ..size.. ..completed.. ..timed-out..)
       => ..track-result..)

 (fact "Non-302 response when resizing ASG throws exception"
       (resize-asg "region" ..asg.. ..ticket.. {:id ..task-id..} ..size.. ..completed.. ..timed-out..)
       => (throws ExceptionInfo "Unexpected status while resizing ASG")
       (provided
        (http/simple-post
         "http://asgard:8080/region/cluster/index"
         {:form-params ..asgard-params..})
        => {:status 500}))

 (fact "Missing ASG when resizing throws exception"
       (resize-asg "region" ..asg.. ..ticket.. {:id ..task-id..} ..size.. ..completed.. ..timed-out..)
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
      => (just {:date ..date..
                :message "Completed in 1s."})
      (provided
       (http/simple-get ..task-url..)
       => {:status 200
           :body "{\"log\":[\"2013-10-11_18:25:23 Completed in 1s.\"]}"}
       (fmt/parse asgard-log-date-formatter "2013-10-11_18:25:23")
       => ..date..))

(fact "Getting a task does updateTime transformations"
      (:updateTime (task-by-url ..task-url..))
      => ..date..
      (provided
       (http/simple-get ..task-url..)
       => {:status 200
           :body "{\"updateTime\":\"2013-10-11 14:20:42 UTC\"}"}
       (fmt/parse asgard-update-time-formatter "2013-10-11 14:20:42 GMT")
       => ..date..))

(fact "Task tracking works for the happy path"
      (track-task "ticket-id" {:url "task-url"} 3600 ..completed.. ..timed-out..)
      => nil
      (provided
       (task-by-url "task-url")
       => {:status "running"}
       (store/store-task "ticket-id" {:status "running" :url "task-url"})
       => nil
       (track-until-completed "ticket-id" {:status "running" :url "task-url"} 3599 ..completed.. ..timed-out..)
       => nil))

(fact "Task tracking sad path attempts to reschedule for :http problem"
      (track-task "ticket-id" {:url "task-url"} 3600 ..completed.. ..timed-out..)
      => nil
      (provided
       (task-by-url "task-url")
       =throws=> (ex-info "Oh god no!" {:class :http})
       (track-until-completed "ticket-id" {:url "task-url"} 3599 ..completed.. ..timed-out..)
       => nil))

(fact "Task tracking sad path attempts to reschedule for :store problem"
      (track-task "ticket-id" {:url "task-url"} 3600 ..completed.. ..timed-out..)
      => nil
      (provided
       (task-by-url "task-url")
       =throws=> (ex-info "Oh god no!" {:class :store})
       (track-until-completed "ticket-id" {:url "task-url"} 3599 ..completed.. ..timed-out..)
       => nil))

(fact "Task tracking sad path blows up for other problem"
      (track-task "ticket-id" {:url "task-url"} 3600 ..completed.. ..timed-out..)
      => (throws ExceptionInfo "Oh god no!")
      (provided
       (task-by-url "task-url")
       =throws=> (ex-info "Oh god no!" {:class :other})))

(against-background
 [(auto-scaling-group "region" ..asg..)
  => {}
  (explode-parameters {:_action_delete ""
                       :name ..asg..
                       :ticket ..ticket..})
  => ..asgard-params..
  (http/simple-post
   "http://asgard:8080/region/cluster/index"
   {:form-params ..asgard-params..})
  => {:status 302
      :headers {"location" "task-url"}}
  (track-until-completed ..ticket.. {:id ..task-id..
                                     :url "task-url.json"
                                     :asgardParameters {:_action_delete ""
                                                        :name ..asg..
                                                        :ticket ..ticket..}} 3600 ..completed.. ..timed-out..)
  => ..track-result..]

 (fact "Deleting ASG returns whatever was returned by `track-until-completed`."
       (delete-asg "region" ..asg.. ..ticket.. {:id ..task-id..} ..completed.. ..timed-out..)
       => ..track-result..)

 (fact "Non-302 response when deleting ASG throws exception"
       (delete-asg "region" ..asg.. ..ticket.. ..task.. ..completed.. ..timed-out..)
       => (throws ExceptionInfo "Unexpected status while deleting ASG")
       (provided
        (http/simple-post
         "http://asgard:8080/region/cluster/index"
         {:form-params ..asgard-params..})
        => {:status 500}))

 (fact "Missing ASG when deleting throws exception"
       (delete-asg "region" ..asg.. ..ticket.. ..task.. ..completed.. ..timed-out..)
       => (throws ExceptionInfo "Auto Scaling Group does not exist.")
       (provided
        (auto-scaling-group "region" ..asg..)
        => nil)))

(against-background
 [(auto-scaling-group "region" ..asg..)
  => {}
  (explode-parameters {:_action_activate ""
                       :name ..asg..
                       :ticket ..ticket..})
  => ..asgard-params..
  (http/simple-post
   "http://asgard:8080/region/cluster/index"
   {:form-params ..asgard-params..})
  => {:status 302
      :headers {"location" "task-url"}}
  (track-until-completed ..ticket.. {:id ..task-id..
                                     :url "task-url.json"
                                     :asgardParameters {:_action_activate ""
                                                        :name ..asg..
                                                        :ticket ..ticket..}} 3600 ..completed.. ..timed-out..)
  => ..track-result..]

 (fact "Enabling ASG returns whatever was returned by `track-until-completed`."
       (enable-asg "region" ..asg.. ..ticket.. {:id ..task-id..} ..completed.. ..timed-out..)
       => ..track-result..)

 (fact "Non-302 response when enabling ASG throws exception"
       (enable-asg "region" ..asg.. ..ticket.. ..task.. ..completed.. ..timed-out..)
       => (throws ExceptionInfo "Unexpected status while enabling ASG")
       (provided
        (http/simple-post
         "http://asgard:8080/region/cluster/index"
         {:form-params ..asgard-params..})
        => {:status 500}))

 (fact "Missing ASG when enabling throws exception"
       (enable-asg "region" ..asg.. ..ticket.. ..task.. ..completed.. ..timed-out..)
       => (throws ExceptionInfo "Auto Scaling Group does not exist.")
       (provided
        (auto-scaling-group "region" ..asg..)
        => nil)))

(against-background
 [(auto-scaling-group "region" ..asg..)
  => {}
  (explode-parameters {:_action_deactivate ""
                       :name ..asg..
                       :ticket ..ticket..})
  => ..asgard-params..
  (http/simple-post
   "http://asgard:8080/region/cluster/index"
   {:form-params ..asgard-params..})
  => {:status 302
      :headers {"location" "task-url"}}
  (track-until-completed ..ticket.. {:id ..task-id..
                                     :url "task-url.json"
                                     :asgardParameters {:_action_deactivate ""
                                                        :name ..asg..
                                                        :ticket ..ticket..}} 3600 ..completed.. ..timed-out..)
  => ..track-result..]

 (fact "Disabling ASG returns whatever was returned by `track-until-completed`."
       (disable-asg "region" ..asg.. ..ticket.. {:id ..task-id..} ..completed.. ..timed-out..)
       => ..track-result..)

 (fact "Non-302 response when disabling ASG throws exception"
       (disable-asg "region" ..asg.. ..ticket.. ..task.. ..completed.. ..timed-out..)
       => (throws ExceptionInfo "Unexpected status while disabling ASG")
       (provided
        (http/simple-post
         "http://asgard:8080/region/cluster/index"
         {:form-params ..asgard-params..})
        => {:status 500}))

 (fact "Missing ASG when disabling throws exception"
       (disable-asg "region" ..asg.. ..ticket.. ..task.. ..completed.. ..timed-out..)
       => (throws ExceptionInfo "Auto Scaling Group does not exist.")
       (provided
        (auto-scaling-group "region" ..asg..)
        => nil)))

(fact "We can find the new ASG name from the `Location` header"
      (extract-new-asg-name "http://asgard/eu-west-1/autoScaling/show/my-new-asg")
      => "my-new-asg")

(against-background
 [(create-new-asg-asgard-parameters "region" "application" "environment" ..ami.. ..user-params.. ..ticket..)
  => ..asgard-params..
  (explode-parameters ..asgard-params..)
  => ..exploded-params..]

 (fact "Create new ASG happy path"
       (create-new-asg "region" "application" "environment" ..ami.. ..user-params.. ..ticket.. {:id ..task-id..} ..completed.. ..timed-out..)
       => "new-asg-name"
       (provided
        (http/simple-post
         "http://asgard:8080/region/autoScaling/save"
         {:form-params ..exploded-params..})
        => {:status 302
            :headers {"location" "http://asgard:8080/region/autoScaling/show/new-asg-name"}}
        (tasks)
        => [{:name "Something we don't care about"}
            {:id 420 :name "Create Auto Scaling Group 'new-asg-name'"}]
        (store/add-to-deployment-parameters ..ticket.. {:newAutoScalingGroupName "application-environment"})
        => ..store-result..
        (track-until-completed ..ticket.. {:id ..task-id..
                                           :url "http://asgard:8080/region/task/show/420.json"
                                           :asgardParameters ..asgard-params..} 3600 ..completed.. ..timed-out..)
        => ..track-result..))

 (fact "Non-302 response when creating new ASG throws exception"
       (create-new-asg "region" "application" "environment" ..ami.. ..user-params.. ..ticket.. ..task.. ..completed.. ..timed-out..)
       => (throws ExceptionInfo "Unexpected status while creating new ASG")
       (provided
        (http/simple-post
         "http://asgard:8080/region/autoScaling/save"
         {:form-params ..exploded-params..})
        => {:status 500})))

(fact "that we can get the next ASG name from a task's log messages"
      (new-asg-name-from-task ..task-url..)
      => "new-asg-name"
      (provided
       (task-by-url ..task-url..)
       => {:log [{:message "Whatever we have going on and then Creating auto scaling group 'new-asg-name', followed by whatever..."}
                 {:message "Another log message, which we'll not pay attention to."}]}))

(fact "that we can get the next ASG from something realistic"
      (new-asg-name-from-task ..task-url..)
      => "recommendations-dev-v000"
      (provided
       (task-by-url ..task-url..)
       => {:log [{:date "2013-10-23T11:37:11.000Z" :message "Started on thread Task:Creating auto scaling group 'recommendations-dev-v000', min 0, max 0, traffic allowed."}] :status "running" :operation "Started on thread Task:Creating auto scaling group 'recommendations-dev-v000', min 0, max 0, traffic allowed." :durationString "0s" :updateTime "2013-10-23T11:37:11.000Z"}))

(against-background
 [(create-next-asg-asgard-parameters "region" "application" "environment" ..ami.. ..user-params.. ..ticket..)
  => ..asgard-params..
  (explode-parameters ..asgard-params..)
  => ..exploded-params..]

 (fact "Create next ASG happy path"
       (create-next-asg "region" "application" "environment" ..ami.. ..user-params.. ..ticket.. {:id ..task-id..} ..completed.. ..timed-out..)
       => ..track-result..
       (provided
        (http/simple-post
         "http://asgard:8080/region/cluster/createNextGroup"
         {:form-params ..exploded-params..})
        => {:status 302
            :headers {"location" "http://asgard:8080/region/task/show/426"}}
        (new-asg-name-from-task "http://asgard:8080/region/task/show/426.json")
        => ..new-asg..
        (store/add-to-deployment-parameters ..ticket.. {:newAutoScalingGroupName ..new-asg..})
        => ..store-result..
        (track-until-completed ..ticket.. {:id ..task-id..
                                           :url "http://asgard:8080/region/task/show/426.json"
                                           :asgardParameters ..asgard-params..} 3600 ..completed.. ..timed-out..)
        => ..track-result..))

 (fact "Non-302 response when creating next ASG throws exception"
       (create-next-asg "region" "application" "environment" ..ami.. ..user-params.. ..ticket.. ..task.. ..completed.. ..timed-out..)
       => (throws ExceptionInfo "Unexpected status while creating next ASG")
       (provided
        (http/simple-post
         "http://asgard:8080/region/cluster/createNextGroup"
         {:form-params ..exploded-params..})
        => {:status 500})))

(fact "that creating a new ASG for an application which already has one puts the name of the old one in the params and creates the next one"
      (create-auto-scaling-group ..region..
                                 "application"
                                 "environment"
                                 ..ami..
                                 ..params..
                                 ..ticket..
                                 ..task..
                                 ..completed..
                                 ..timed-out..)
      => ..create-result..
      (provided
       (last-auto-scaling-group ..region.. "application-environment")
       => {:autoScalingGroupName ..old-asg..}
       (store/add-to-deployment-parameters ..ticket.. {:oldAutoScalingGroupName ..old-asg..})
       => ..store-result..
       (create-next-asg ..region.. "application" "environment" ..ami.. ..params.. ..ticket.. ..task.. ..completed.. ..timed-out..)
       => ..create-result..))
