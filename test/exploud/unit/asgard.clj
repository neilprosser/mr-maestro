(ns exploud.unit.asgard
  (:require [exploud
             [asgard_new :refer :all]
             [http :as http]
             [store :as store]
             [tyranitar :as tyr]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact-group

            (fact "We can retrieve the details about an Auto Scaling Group from Asgard"
                  (auto-scaling-group ..region.. ..asg..)
                  => {:name "the-name"}
                  (provided
                   (http/simple-get
                    "http://asgard:8080/..region../autoScaling/show/..asg...json")
                   => {:status 200
                       :body "{\"name\":\"the-name\"}"}))

            (fact "A missing ASG comes back with nil"
                  (auto-scaling-group ..region.. ..asg..)
                  => nil
                  (provided
                   (http/simple-get
                    "http://asgard:8080/..region../autoScaling/show/..asg...json") => {:status 404}))

            (against-background
             [(auto-scaling-group ..region.. ..asg..) => {}
              (http/simple-post
               "http://asgard:8080/..region../cluster/index"
               {:form-params {"_action_resize" ""
                              "minAndMaxSize" ..size..
                              "name" ..asg..
                              "ticket" ..ticket..}}) => {:status 302
                                                         :headers {"location" ..task-url..}}
                              (track-until-completed ..ticket.. {:id ..task-id.. :url ..task-url..} 3600) => ..track-result..]

             (fact "Resizing ASG returns whatever was returned by `track-until-completed`."
                   (resize-asg ..region.. ..asg.. ..ticket.. {:id ..task-id..} ..size..)
                   => ..track-result..)

             (fact "Non-302 response when resizing ASG throws exception"
                   (resize-asg ..region.. ..asg.. ..ticket.. {:id ..task-id..} ..size..)
                   => (throws ExceptionInfo "Unexpected status while resizing ASG")
                   (provided
                    (http/simple-post
                     "http://asgard:8080/..region../cluster/index"
                     {:form-params {"_action_resize" ""
                                    "minAndMaxSize" ..size..
                                    "name" ..asg..
                                    "ticket" ..ticket..}}) => {:status 500}))

             (fact "Missing ASG when resizing throws exception"
                   (resize-asg ..region.. ..asg.. ..ticket.. {:id ..task-id..} ..size..)
                   => (throws ExceptionInfo "Auto Scaling Group does not exist.")
                   (provided
                    (auto-scaling-group ..region.. ..asg..) => nil)))

            (fact "We can tell whether a task is finished"
                  (finished? {:status "completed"}) => true
                  (finished? {:status "failed"}) => true
                  (finished? {:status "terminated"}) => true
                  (finished? {:status "running"}) => false)

            (fact "Getting a task does log transformations"
                  (first (:log (task-by-url ..task-url..)))
                  => (just {:date "2013-10-11T18:25:23Z"
                            :message "Completed in 1s."})
                  (provided
                   (http/simple-get ..task-url..) => {:status 200
                                                      :body "{\"log\":[\"2013-10-11_18:25:23 Completed in 1s.\"]}"}))

            (fact "Getting a task does updateTime transformations"
                  (:updateTime (task-by-url ..task-url..))
                  => "2013-10-11T14:20:42Z"
                  (provided
                   (http/simple-get ..task-url..) => {:status 200
                                                      :body "{\"updateTime\":\"2013-10-11 14:20:42 UTC\"}"}))

            (fact "Task tracking works for the happy path"
                  (track-task "ticket-id" {:url "task-url"} 3600)
                  => nil
                  (provided
                   (task-by-url "task-url") => {:status "running"}
                   (store/store-task {:status "running" :url "task-url"}) => nil
                   (track-until-completed "ticket-id" {:url "task-url"} 3599) => nil))

            (fact "Task tracking sad path attempts to reschedule for :http problem"
                  (track-task "ticket-id" {:url "task-url"} 3600)
                  => nil
                  (provided
                   (task-by-url "task-url") =throws=> (ex-info "Oh god no!" {:class :http})
                   (track-until-completed "ticket-id" {:url "task-url"} 3599) => nil))

            (fact "Task tracking sad path attempts to reschedule for :store problem"
                  (track-task "ticket-id" {:url "task-url"} 3600)
                  => nil
                  (provided
                   (task-by-url "task-url") =throws=> (ex-info "Oh god no!" {:class :store})
                   (track-until-completed "ticket-id" {:url "task-url"} 3599) => nil))

            (fact "Task tracking sad path blows up for other problem"
                  (track-task "ticket-id" {:url "task-url"} 3600)
                  => (throws ExceptionInfo "Oh god no!")
                  (provided
                   (task-by-url "task-url") =throws=> (ex-info "Oh god no!" {:class :other})))

            (against-background
             [(auto-scaling-group ..region.. ..asg..) => {}
              (http/simple-post
               "http://asgard:8080/..region../cluster/index"
               {:form-params {"_action_delete" ""
                              "name" ..asg..
                              "ticket" ..ticket..}}) => {:status 302
                                                         :headers {"location" ..task-url..}}
                              (track-until-completed ..ticket.. {:id ..task-id.. :url ..task-url..} 3600) => ..track-result..]

             (fact "Deleting ASG returns whatever was returned by `track-until-completed`."
                   (delete-asg ..region.. ..asg.. ..ticket.. {:id ..task-id..})
                   => ..track-result..)

             (fact "Non-302 response when deleting ASG throws exception"
                   (delete-asg ..region.. ..asg.. ..ticket.. ..task..)
                   => (throws ExceptionInfo "Unexpected status while deleting ASG")
                   (provided
                    (http/simple-post
                     "http://asgard:8080/..region../cluster/index"
                     {:form-params {"_action_delete" ""
                                    "name" ..asg..
                                    "ticket" ..ticket..}}) => {:status 500}))

             (fact "Missing ASG when deleting throws exception"
                   (delete-asg ..region.. ..asg.. ..ticket.. ..task..)
                   => (throws ExceptionInfo "Auto Scaling Group does not exist.")
                   (provided
                    (auto-scaling-group ..region.. ..asg..) => nil)))

            (against-background
             [(auto-scaling-group ..region.. ..asg..) => {}
              (http/simple-post
               "http://asgard:8080/..region../cluster/index"
               {:form-params {"_action_activate" ""
                              "name" ..asg..
                              "ticket" ..ticket..}}) => {:status 302
                                                         :headers {"location" ..task-url..}}
                              (track-until-completed ..ticket.. {:id ..task-id.. :url ..task-url..} 3600) => ..track-result..]

             (fact "Enabling ASG returns whatever was returned by `track-until-completed`."
                   (enable-asg ..region.. ..asg.. ..ticket.. {:id ..task-id..})
                   => ..track-result..)

             (fact "Non-302 response when enabling ASG throws exception"
                   (enable-asg ..region.. ..asg.. ..ticket.. ..task..)
                   => (throws ExceptionInfo "Unexpected status while enabling ASG")
                   (provided
                    (http/simple-post
                     "http://asgard:8080/..region../cluster/index"
                     {:form-params {"_action_activate" ""
                                    "name" ..asg..
                                    "ticket" ..ticket..}}) => {:status 500}))

             (fact "Missing ASG when enabling throws exception"
                   (enable-asg ..region.. ..asg.. ..ticket.. ..task..)
                   => (throws ExceptionInfo "Auto Scaling Group does not exist.")
                   (provided
                    (auto-scaling-group ..region.. ..asg..) => nil)))

            (against-background
             [(auto-scaling-group ..region.. ..asg..) => {}
              (http/simple-post
               "http://asgard:8080/..region../cluster/index"
               {:form-params {"_action_deactivate" ""
                              "name" ..asg..
                              "ticket" ..ticket..}}) => {:status 302
                                                         :headers {"location" ..task-url..}}
                              (track-until-completed ..ticket.. {:id ..task-id.. :url ..task-url..} 3600) => ..track-result..]

             (fact "Disabling ASG returns whatever was returned by `track-until-completed`."
                   (disable-asg ..region.. ..asg.. ..ticket.. {:id ..task-id..})
                   => ..track-result..)

             (fact "Non-302 response when disabling ASG throws exception"
                   (disable-asg ..region.. ..asg.. ..ticket.. ..task..)
                   => (throws ExceptionInfo "Unexpected status while disabling ASG")
                   (provided
                    (http/simple-post
                     "http://asgard:8080/..region../cluster/index"
                     {:form-params {"_action_deactivate" ""
                                    "name" ..asg..
                                    "ticket" ..ticket..}}) => {:status 500}))

             (fact "Missing ASG when disabling throws exception"
                   (disable-asg ..region.. ..asg.. ..ticket.. ..task..)
                   => (throws ExceptionInfo "Auto Scaling Group does not exist.")
                   (provided
                    (auto-scaling-group ..region.. ..asg..) => nil)))

            (fact "We can find the new ASG name from the `Location` header"
                  (extract-new-asg-name "http://asgard/eu-west-1/autoScaling/show/my-new-asg") => "my-new-asg")

            (against-background
             [(tyr/deployment-params ..environment.. ..application.. ..hash..) => {"from" "user"}
              (http/simple-post
               "http://asgard:8080/..region../autoScaling/save"
               {:form-params {"_action_save" ""
                              "appName" ..application..
                              "from" "user"
                              "imageId" ..ami..
                              "keyName" "nprosser-key"
                              "stack" ..environment..
                              "ticket" ..ticket..}})
              => {:status 302
                  :headers {"location" "http://asgard:8080/..region../autoScaling/show/new-asg-name"}}
              (tasks ..region..)
              => [{:name "Something we don't care about"}
                  {:id 420 :name "Create Auto Scaling Group 'new-asg-name'"}]
              (track-until-completed ..ticket.. {:id ..task-id..
                                                 :url "http://asgard:8080/..region../task/show/420.json"} 3600)
              => ..track-result..]

             (fact "Create new ASG happy path"
                   (create-new-asg ..region.. ..application.. ..environment.. ..ami.. ..hash.. ..ticket.. {:id ..task-id..})
                   => "new-asg-name")

             (fact "Non-302 response when creating next ASG throws exception"
                   (create-new-asg ..region.. ..application.. ..environment.. ..ami.. ..hash.. ..ticket.. ..task..)
                   => (throws ExceptionInfo "Unexpected status while creating new ASG")
                   (provided
                    (http/simple-post
                     "http://asgard:8080/..region../autoScaling/save"
                     {:form-params {"_action_save" ""
                                    "appName" ..application..
                                    "from" "user"
                                    "imageId" ..ami..
                                    "keyName" "nprosser-key"
                                    "stack" ..environment..
                                    "ticket" ..ticket..}}) => {:status 500})))

            (against-background
             [(tyr/deployment-params ..environment.. ..application.. ..hash..) => {"from" "user"}]

             (fact "Create next ASG happy path"
                   (create-next-asg ..region.. ..application.. ..environment.. ..ami.. ..hash.. ..ticket.. {:id ..task-id..})
                   => nil
                   (provided
                    (http/simple-post
                     "http://asgard:8080/..region../cluster/createNextGroup"
                     {:form-params {"_action_createNextGroup" ""
                                    "from" "user"
                                    "imageId" ..ami..
                                    "keyName" "nprosser-key"
                                    "name" "..application..-..environment.."
                                    "ticket" ..ticket..
                                    "trafficAllowed" "off"}})
                    => {:status 302
                        :headers {"location" "http://asgard:8080/..region../task/show/426"}}
                    (track-until-completed ..ticket.. {:id ..task-id..
                                                       :url "http://asgard:8080/..region../task/show/426.json"} 3600)
                    => ..track-result..))

             (fact "Non-302 response when creating next ASG throws exception"
                   (create-next-asg ..region.. ..application.. ..environment.. ..ami.. ..hash.. ..ticket.. ..task..)
                   => (throws ExceptionInfo "Unexpected status while creating next ASG")
                   (provided
                    (http/simple-post
                     "http://asgard:8080/..region../cluster/createNextGroup"
                     {:form-params {"_action_createNextGroup" ""
                                    "from" "user"
                                    "imageId" ..ami..
                                    "keyName" "nprosser-key"
                                    "name" "..application..-..environment.."
                                    "ticket" ..ticket..
                                    "trafficAllowed" "off"}}) => {:status 500}))))
