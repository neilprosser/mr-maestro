(ns maestro.web-test
  (:require [bouncer.core :as b]
            [cheshire.core :as json]
            [clj-time.format :as fmt]
            [maestro
             [aws :as aws]
             [deployments :as deployments]
             [elasticsearch :as es]
             [environments :as environments]
             [identity :as id]
             [images :as images]
             [info :as info]
             [redis :as redis]
             [util :as util]
             [validators :as v]
             [web :refer :all]]
            [midje.sweet :refer :all]
            [ring.util.io :refer [string-input-stream]]))

(def valid-deployment-id
  "6e653196-3b52-4ac8-a273-96a8ebd74060")

(defn- json-body
  [raw-body]
  {:body (string-input-stream (json/encode raw-body))
   :headers {"content-type" "application/json"}})

(defn- streamed-body?
  [{:keys [body]}]
  (instance? java.io.InputStream body))

(defn- json-response?
  [{:keys [headers]}]
  (when-let [content-type (get headers "Content-Type")]
    (re-find #"^application/(vnd.+)?json" content-type)))

(defn request
  "Creates a compojure request map and applies it to our application.
   Accepts method, resource and optionally an extended map"
  [method resource & [{:keys [body headers params]
                       :or {:body nil
                            :headers {}
                            :params {}}}]]
  (let [{:keys [body headers] :as response} (app {:body body
                                                  :headers headers
                                                  :params params
                                                  :request-method method
                                                  :uri resource})]
    (cond-> response
            (streamed-body? response) (update-in [:body] slurp)
            (json-response? response) (update-in [:body] (fn [b] (json/parse-string b true))))))

(fact "that ping pongs"
      (request :get "/ping") => (contains {:body "pong"
                                           :status 200}))

(fact "that our healthcheck gives 200 when everything is great"
      (request :get "/healthcheck") => (contains {:status 200})
      (provided
       (environments/healthy?) => true
       (es/healthy?) => true
       (id/healthy?) => true
       (redis/healthy?) => true))

(fact "that our healthcheck gives 500 when environments are sad"
      (request :get "/healthcheck") => (contains {:status 500})
      (provided
       (environments/healthy?) => false
       (es/healthy?) => true
       (id/healthy?) => true
       (redis/healthy?) => true))

(fact "that our healthcheck gives 500 when Elasticsearch is sad"
      (request :get "/healthcheck") => (contains {:status 500})
      (provided
       (environments/healthy?) => true
       (es/healthy?) => false
       (id/healthy?) => true
       (redis/healthy?) => true))

(fact "that our healthcheck gives 500 when the instance metadata is sad"
      (request :get "/healthcheck") => (contains {:status 500})
      (provided
       (environments/healthy?) => true
       (es/healthy?) => true
       (id/healthy?) => false
       (redis/healthy?) => true))

(fact "that our healthcheck gives 500 when Redis is sad"
      (request :get "/healthcheck") => (contains {:status 500})
      (provided
       (environments/healthy?) => true
       (es/healthy?) => true
       (id/healthy?) => true
       (redis/healthy?) => false))

(fact "that we can retrieve the queue status"
      (request :get "/queue-status") => (contains {:body {:queue "status"}
                                                   :status 200})
      (provided
       (redis/queue-status) => {:queue "status"}))

(fact "that getting the status of the lock when Maestro is unlocked gives 404"
      (request :get "/lock") => (contains {:status 404})
      (provided
       (deployments/locked?) => false))

(fact "that getting the status of the lock when Maestro is locked gives 200"
      (request :get "/lock") => (contains {:status 200})
      (provided
       (deployments/locked?) => true))

(fact "that attempting to lock Maestro works"
      (request :post "/lock") => (contains {:status 204})
      (provided
       (deployments/lock) => anything))

(fact "that attempting to unlock Maestro works"
      (request :delete "/lock") => (contains {:status 204})
      (provided
       (deployments/unlock) => anything))

(fact "that getting deployments with no parameters works"
      (request :get "/deployments")
      => (contains {:body {:deployments []}})
      (provided
       (es/get-deployments {:application nil
                            :environment nil
                            :from nil
                            :full? false
                            :region nil
                            :size nil
                            :start-from nil
                            :start-to nil
                            :status nil})
       => []))

(fact "that getting deployments with a zero size gives a 400"
      (request :get "/deployments" {:params {:size "0"}})
      => (contains {:status 400}))

(fact "that getting deployments with a positive size works"
      (request :get "/deployments" {:params {:size "12"}})
      => (contains {:body {:deployments []}})
      (provided
       (es/get-deployments {:application nil
                            :environment nil
                            :from nil
                            :full? false
                            :region nil
                            :size 12
                            :start-from nil
                            :start-to nil
                            :status nil})
       => []))

(fact "that getting deployments with a non-integer size gives a 400"
      (request :get "/deployments" {:params {:size "adsdsads"}})
      => (contains {:status 400}))

(fact "that getting deployments with a negative size gives a 400"
      (request :get "/deployments" {:params {:size "-12"}})
      => (contains {:status 400}))

(fact "that getting deployments with a zero from works"
      (request :get "/deployments" {:params {:from "0"}})
      => (contains {:body {:deployments []}})
      (provided
       (es/get-deployments {:application nil
                            :environment nil
                            :from 0
                            :full? false
                            :region nil
                            :size nil
                            :start-from nil
                            :start-to nil
                            :status nil})
       => []))

(fact "that getting deployments with a positive from works"
      (request :get "/deployments" {:params {:from "12"}})
      => (contains {:body {:deployments []}})
      (provided
       (es/get-deployments {:application nil
                            :environment nil
                            :from 12
                            :full? false
                            :region nil
                            :size nil
                            :start-from nil
                            :start-to nil
                            :status nil})
       => []))

(fact "that getting deployments with a non-integer from gives a 400"
      (request :get "/deployments" {:params {:from "adsdsads"}})
      => (contains {:status 400}))

(fact "that getting deployments with a negative from gives a 400"
      (request :get "/deployments" {:params {:from "-12"}})
      => (contains {:status 400}))

(fact "that getting deployments with a valid start-from works"
      (request :get "/deployments" {:params {:start-from "2013-10-10"}})
      => (contains {:body {:deployments []}})
      (provided
       (fmt/parse "2013-10-10")
       => ..date..
       (fmt/parse nil)
       => nil
       (es/get-deployments {:application nil
                            :environment nil
                            :from nil
                            :full? false
                            :region nil
                            :size nil
                            :start-from ..date..
                            :start-to nil
                            :status nil})
       => []))

(fact "that getting deployments with an invalid start-from gives a 400"
      (request :get "/deployments" {:params {:start-from "not a date"}})
      => (contains {:status 400}))

(fact "that getting deployments with a valid start-to works"
      (request :get "/deployments" {:params {:start-to "2013-10-10"}})
      => (contains {:body {:deployments []}})
      (provided
       (fmt/parse "2013-10-10")
       => ..date..
       (fmt/parse nil)
       => nil
       (es/get-deployments {:application nil
                            :environment nil
                            :from nil
                            :full? false
                            :region nil
                            :size nil
                            :start-from nil
                            :start-to ..date..
                            :status nil})
       => []))

(fact "that getting deployments with an invalid start-to gives a 400"
      (request :get "/deployments" {:params {:start-to "not a date"}})
      => (contains {:status 400}))

(fact "that getting deployments with a valid full works"
      (request :get "/deployments" {:params {:full "true"}})
      => (contains {:body {:deployments []}})
      (provided
       (es/get-deployments {:application nil
                            :environment nil
                            :from nil
                            :full? true
                            :region nil
                            :size nil
                            :start-from nil
                            :start-to nil
                            :status nil}) => []))

(fact "that getting deployments with an invalid full gives a 400"
      (request :get "/deployments" {:params {:full "fdasdsdas"}})
      => (contains {:status 400}))

(fact "that getting an individual deployment with an invalid ID gives a 400"
      (request :get "/deployments/id")
      => (contains {:status 400})
      (provided
       (es/deployment anything) => nil :times 0))

(fact "that getting an individual deployment gives a 404 if it doesn't exist"
      (request :get (str "/deployments/" valid-deployment-id))
      => (contains {:status 404})
      (provided
       (es/deployment valid-deployment-id) => nil))

(fact "that getting an individual deployment gives it back when it exists"
      (request :get (str "/deployments/" valid-deployment-id))
      => (contains {:body {:id valid-deployment-id
                           :something "deployment-like"}
                    :status 200})
      (provided
       (es/deployment valid-deployment-id) => {:id valid-deployment-id
                                               :something "deployment-like"}))

(fact "that deleting a deployment using an invalid ID gives a 400"
      (request :delete "/deployments/id")
      => (contains {:status 400})
      (provided
       (es/delete-deployment anything) => nil :times 0))

(fact "that deleting a deployment gives the right response"
      (request :delete (str "/deployments/" valid-deployment-id))
      => (contains {:status 204})
      (provided
       (es/delete-deployment valid-deployment-id) => anything))

(fact "that getting deployment tasks using an invalid ID gives a 400"
      (request :get "/deployments/id/tasks")
      => (contains {:status 400})
      (provided
       (es/deployment-tasks anything) => nil :times 0))

(fact "that getting deployment tasks for a deployment which doesn't exist gives a 404"
      (request :get (str "/deployments/" valid-deployment-id "/tasks"))
      => (contains {:status 404})
      (provided
       (es/deployment-tasks valid-deployment-id) => nil))

(fact "that getting deployment tasks for a deployment with no tasks gives a 200"
      (request :get (str "/deployments/" valid-deployment-id "/tasks"))
      => (contains {:body {:tasks []}
                    :status 200})
      (provided
       (es/deployment-tasks valid-deployment-id) => []))

(fact "that getting deployment tasks for a deployment with some tasks gives a 200"
      (request :get (str "/deployments/" valid-deployment-id "/tasks"))
      => (contains {:body {:tasks [{:some "task"}
                                   {:some "other-task"}]}
                    :status 200})
      (provided
       (es/deployment-tasks valid-deployment-id) => [{:some "task"}
                                                     {:some "other-task"}]))

(fact "that getting deployment logs using an invalid ID gives a 400"
      (request :get "/deployments/id/logs")
      => (contains {:status 400})
      (provided
       (es/deployment-logs anything anything) => nil :times 0))

(fact "that getting deployment logs for a deployment which doesn't exist gives a 404"
      (request :get (str "/deployments/" valid-deployment-id "/logs"))
      => (contains {:status 404})
      (provided
       (es/deployment-logs valid-deployment-id nil) => nil))

(fact "that getting deployment logs without a since date passes nil to the underlying function"
      (request :get (str "/deployments/" valid-deployment-id "/logs") {:params {}})
      => (contains {:body {:logs [{:a "log"}]}})
      (provided
       (es/deployment-logs valid-deployment-id nil) => [{:a "log"}]))

(fact "that getting deployment logs with an invalid since date returns 400"
      (request :get (str "/deployments/" valid-deployment-id "/logs") {:params {:since "garbage"}})
      => (contains {:status 400}))

(fact "that getting deployment logs with a valid since date does the right thing"
      (request :get (str "/deployments/" valid-deployment-id "/logs") {:params {:since "2003-01-04T00:02:01Z"}})
      => (contains {:body {:logs [{:a "log"}]}})
      (provided
       (fmt/parse "2003-01-04T00:02:01Z") => ..date..
       (es/deployment-logs valid-deployment-id ..date..) => [{:a "log"}]))

(fact "that getting the list of applications does the right thing"
      (request :get "/applications")
      => (contains {:body {:applications ["applications"]}
                    :status 200})
      (provided
       (info/applications) => {:applications ["applications"]}))

(fact "that getting an application gives a 400 if the application name is invalid"
      (request :get "/applications/has-hyphen")
      => (contains {:status 400})
      (provided
       (info/application anything) => nil :times 0))

(fact "that getting an application gives a 404 if the application doesn't exist"
      (request :get "/applications/application")
      => (contains {:status 404})
      (provided
       (info/application "application") => nil))

(fact "that getting an application gives the details of the application"
      (request :get "/applications/application")
      => (contains {:body {:application "details"}
                    :status 200})
      (provided
       (info/application "application") => {:application "details"}))

(fact "that getting the prohibited images for an invalid application gives a 400"
      (request :get "/applications/has-hyphen/prohibited-images")
      => (contains {:status 400})
      (provided
       (images/prohibited-images anything anything) => nil :times 0))

(fact "that getting the prohibited images for an application does what we expect"
      (request :get "/applications/application/prohibited-images")
      => (contains {:status 200
                    :body {:images ["ami-1" "ami-2"]}})
      (provided
       (images/prohibited-images "application" "eu-west-1") => ["ami-1" "ami-2"]))

(fact "that attempting to create an application while Maestro is locked gives 409"
      (request :put "/applications/application")
      => (contains {:status 409})
      (provided
       (deployments/locked?) => true))

(fact "that creating an application with an illegal name returns 400"
      (request :put "/applications/my-application")
      => (contains {:status 400})
      (provided
       (deployments/locked?) => false))

(fact "that creating an application with a legal name returns 201"
      (request :put "/applications/myapplication")
      => (contains {:status 201})
      (provided
       (deployments/locked?) => false
       (info/upsert-application anything "myapplication" anything) => {}))

(fact "that attempting to deploy an application while Maestro is locked gives 409"
      (request :post "/applications/application/environment/deploy" (json-body {:ami "ami"
                                                                                :hash "hash"
                                                                                :message "message"
                                                                                :silent false
                                                                                :user "user"}))
      => (contains {:status 409})
      (provided
       (deployments/locked?) => true))

(fact "that starting a deployment which is invalid gives a 400"
      (request :post "/applications/application/environment/deploy" (json-body {:ami "ami"
                                                                                :hash "hash"
                                                                                :message "message"
                                                                                :silent false
                                                                                :user "user"}))
      => (contains {:status 400})
      (provided
       (deployments/locked?) => false
       (b/validate {:ami "ami"
                    :application "application"
                    :environment "environment"
                    :hash "hash"
                    :message "message"
                    :silent false
                    :user "user"}
                   v/deployment-request-validators) => ["busted"]))

(fact "that starting a deployment works"
      (request :post "/applications/application/environment/deploy" (json-body {:ami "ami-00000000"
                                                                                :hash "db0adbdcf61e4237e1d116834e185aa06cb682ff"
                                                                                :message "message"
                                                                                :silent false
                                                                                :user "user"}))
      => (contains {:status 200})
      (provided
       (deployments/locked?) => false
       (environments/environments) => {:environment {}}
       (util/generate-id) => "id"
       (deployments/begin {:application "application"
                           :environment "environment"
                           :id "id"
                           :message "message"
                           :new-state {:hash "db0adbdcf61e4237e1d116834e185aa06cb682ff"
                                       :image-details {:id "ami-00000000"}}
                           :region "eu-west-1"
                           :silent false
                           :user "user"}) => ..begin-result..))

(fact "that attempting to undo a deployment while Maestro is locked gives a 409"
      (request :post "/applications/application/environment/undo" (json-body {:message "message"
                                                                              :silent false
                                                                              :user "user"}))
      => (contains {:status 409})
      (provided
       (deployments/locked?) => true))

(fact "that attempting to undo a deployment using an invalid application name gives a 400"
      (request :post "/applications/has-hyphen/environment/undo" (json-body {:message "message"
                                                                             :silent false
                                                                             :user "user"}))
      => (contains {:status 400})
      (provided
       (deployments/locked?) => false
       (environments/environments) => {:environment {}}))

(fact "that undoing a deployment calls through to deployments"
      (request :post "/applications/application/environment/undo" (json-body {:message "message"
                                                                              :silent false
                                                                              :user "user"}))
      => (contains {:status 200})
      (provided
       (deployments/locked?) => false
       (environments/environments) => {:environment {}}
       (deployments/undo {:application "application"
                          :environment "environment"
                          :message "message"
                          :region "eu-west-1"
                          :silent false
                          :user "user"}) => ..undo-result..))

(fact "that attempting to redeploy an application while Maestro is locked gives a 409"
      (request :post "/applications/application/environment/redeploy" (json-body {:message "message"
                                                                                  :silent false
                                                                                  :user "user"}))
      => (contains {:status 409})
      (provided
       (deployments/locked?) => true))

(fact "that attempting to redeploy an application using an invalid application name gives a 400"
      (request :post "/applications/with-hyphen/environment/redeploy" (json-body {:message "message"
                                                                                  :silent false
                                                                                  :user false}))
      => (contains {:status 400})
      (provided
       (deployments/locked?) => false
       (environments/environments) => {:environment {}}))

(fact "that redeploying an application calls through to deployments"
      (request :post "/applications/application/environment/redeploy" (json-body {:message "message"
                                                                                  :silent false
                                                                                  :user "user"}))
      => (contains {:status 200})
      (provided
       (deployments/locked?) => false
       (environments/environments) => {:environment {}}
       (util/generate-id) => "id"
       (deployments/redeploy {:application "application"
                              :environment "environment"
                              :id "id"
                              :message "message"
                              :region "eu-west-1"
                              :silent false
                              :user "user"}) => ..redeploy-result..))

(fact "that attempting to roll-back a deployment while Maestro is locked gives a 409"
      (request :post "/applications/application/environment/rollback" (json-body {:message "message"
                                                                                  :silent false
                                                                                  :user "user"}))
      => (contains {:status 409})
      (provided
       (deployments/locked?) => true))

(fact "that attempting to roll-back a deployment using an invalid application name gives a 400"
      (request :post "/applications/has-hyphen/environment/rollback" (json-body {:message "message"
                                                                                 :silent false
                                                                                 :user "user"}))
      => (contains {:status 400})
      (provided
       (deployments/locked?) => false
       (environments/environments) => {:environment {}}))

(fact "that rolling-back a deployment calls through to deployments"
      (request :post "/applications/application/environment/rollback" (json-body {:message "message"
                                                                                  :silent false
                                                                                  :user "user"}))
      => (contains {:status 200})
      (provided
       (deployments/locked?) => false
       (environments/environments) => {:environment {}}
       (util/generate-id) => "id"
       (deployments/rollback {:application "application"
                              :environment "environment"
                              :id "id"
                              :message "message"
                              :region "eu-west-1"
                              :rollback true
                              :silent false
                              :user "user"}) => ..rollback-result..))

(fact "that attempting to pause a deployment using an invalid application name gives a 400"
      (request :post "/applications/with-hyphen/environment/pause")
      => (contains {:status 400})
      (provided
       (environments/environments) => {:environment {}}))

(fact "that attempting to pause a deployment for something which isn't deploying gives a 409"
      (request :post "/applications/application/environment/pause")
      => (contains {:status 409})
      (provided
       (environments/environments) => {:environment {}}
       (deployments/in-progress? {:application "application" :environment "environment" :region "eu-west-1"}) => false))

(fact "that attempting to pause a deployment which is in-progress works"
      (request :post "/applications/application/environment/pause")
      => (contains {:status 200})
      (provided
       (environments/environments) => {:environment {}}
       (deployments/in-progress? {:application "application" :environment "environment" :region "eu-west-1"}) => true
       (deployments/register-pause {:application "application" :environment "environment" :region "eu-west-1"}) => anything))

(fact "that attempting to unregister a pause for an invalid application gives a 400"
      (request :delete "/applications/with-hyphen/environment/pause")
      => (contains {:status 400})
      (provided
       (environments/environments) => {:environment {}}))

(fact "that attempting to unregister a pause for something which isn't paused gives a 409"
      (request :delete "/applications/application/environment/pause")
      => (contains {:status 409})
      (provided
       (environments/environments) => {:environment {}}
       (deployments/pause-registered? {:application "application" :environment "environment" :region "eu-west-1"}) => false))

(fact "that attempting to unregister a pause for something which is paused gives a 200"
      (request :delete "/applications/application/environment/pause")
      => (contains {:status 200})
      (provided
       (environments/environments) => {:environment {}}
       (deployments/pause-registered? {:application "application" :environment "environment" :region "eu-west-1"}) => true
       (deployments/unregister-pause {:application "application" :environment "environment" :region "eu-west-1"}) => anything))

(fact "that attempting to resume a deployment while Maestro is locked gives a 409"
      (request :post "/applications/application/environment/resume")
      => (contains {:status 409})
      (provided
       (deployments/locked?) => true))

(fact "that attempting to resume a deployment using an invalid application name gives a 400"
      (request :post "/applications/with-hyphen/environment/resume")
      => (contains {:status 400})
      (provided
       (deployments/locked?) => false
       (environments/environments) => {:environment {}}))

(fact "that attempting to resume a deployment which isn't paused gives a 409"
      (request :post "/applications/application/environment/resume")
      => (contains {:status 409})
      (provided
       (deployments/locked?) => false
       (environments/environments) => {:environment {}}
       (deployments/paused? {:application "application" :environment "environment" :region "eu-west-1"}) => false))

(fact "that attempting to resume a deployment which is paused gives a 200"
      (request :post "/applications/application/environment/resume")
      => (contains {:status 200})
      (provided
       (deployments/locked?) => false
       (environments/environments) => {:environment {}}
       (deployments/paused? {:application "application" :environment "environment" :region "eu-west-1"}) => true
       (deployments/resume {:application "application" :environment "environment" :region "eu-west-1"}) => anything))

(fact "that attempting to cancel a deployment using an invalid application name gives a 400"
      (request :post "/applications/with-hyphen/environment/cancel")
      => (contains {:status 400})
      (provided
       (environments/environments) => {:environment {}}))

(fact "that attempting to cancel a deployment for something which isn't deploying gives a 409"
      (request :post "/applications/application/environment/cancel")
      => (contains {:status 409})
      (provided
       (environments/environments) => {:environment {}}
       (deployments/in-progress? {:application "application" :environment "environment" :region "eu-west-1"}) => false))

(fact "that attempting to cancel a deployment which is in-progress works"
      (request :post "/applications/application/environment/cancel")
      => (contains {:status 200})
      (provided
       (environments/environments) => {:environment {}}
       (deployments/in-progress? {:application "application" :environment "environment" :region "eu-west-1"}) => true
       (deployments/register-cancel {:application "application" :environment "environment" :region "eu-west-1"}) => anything))

(fact "that attempting to unregister a cancellation for an invalid application gives a 400"
      (request :delete "/applications/with-hyphen/environment/cancel")
      => (contains {:status 400})
      (provided
       (environments/environments) => {:environment {}}))

(fact "that attempting to unregister a cancellation for something which isn't cancelled gives a 409"
      (request :delete "/applications/application/environment/cancel")
      => (contains {:status 409})
      (provided
       (environments/environments) => {:environment {}}
       (deployments/cancel-registered? {:application "application" :environment "environment" :region "eu-west-1"}) => false))

(fact "that attempting to unregister a cancellation for something which is cancelled gives a 200"
      (request :delete "/applications/application/environment/cancel")
      => (contains {:status 200})
      (provided
       (environments/environments) => {:environment {}}
       (deployments/cancel-registered? {:application "application" :environment "environment" :region "eu-west-1"}) => true
       (deployments/unregister-cancel {:application "application" :environment "environment" :region "eu-west-1"}) => anything))

(fact "that attempting to retry a deployment while Maestro is locked gives a 409"
      (request :post "/applications/application/environment/retry")
      => (contains {:status 409})
      (provided
       (deployments/locked?) => true))

(fact "that attempting to retry a deployment using an invalid application name gives a 400"
      (request :post "/applications/with-hyphen/envivronment/retry")
      => (contains {:status 400})
      (provided
       (deployments/locked?) => false
       (environments/environments) => {:environment {}}))

(fact "that attempting to retry a deployment which cannot be retried gives a 409"
      (request :post "/applications/application/environment/retry")
      => (contains {:status 409})
      (provided
       (deployments/locked?) => false
       (environments/environments) => {:environment {}}
       (deployments/can-retry? {:application "application" :environment "environment" :region "eu-west-1"}) => false))

(fact "that attempting to retry a deployment which can be retried gives a 200"
      (request :post "/applications/application/environment/retry")
      => (contains {:status 200})
      (provided
       (deployments/locked?) => false
       (environments/environments) => {:environment {}}
       (deployments/can-retry? {:application "application" :environment "environment" :region "eu-west-1"}) => true
       (deployments/retry {:application "application" :environment "environment" :region "eu-west-1"}) => ..retry-result..))

(fact "that attempting to resize an auto-scaling group while Maestro is locks gives a 409"
      (request :post "/applications/application/environment/resize" (json-body {:desiredCapacity 2
                                                                                :max 2
                                                                                :min 2}))
      => (contains {:status 409})
      (provided
       (deployments/locked?) => true))

(fact "that attempting to resize an auto-scaling group using non-integers gives a 400"
      (request :post "/applications/application/environment/resize" (json-body {:desiredCapacity "a"
                                                                                :max 3
                                                                                :min 1}))
      => (contains {:status 400})
      (provided
       (deployments/locked?) => false))

(fact "that attempting to resize an auto-scaling group does what it should"
      (request :post "/applications/application/environment/resize" (json-body {:desiredCapacity 2
                                                                                :max 3
                                                                                :min 1}))
      => (contains {:status 201})
      (provided
       (deployments/locked?) => false
       (aws/resize-last-auto-scaling-group "environment" "application" "eu-west-1" 2 3 1) => ..resize-result..))

(fact "that getting the list of environments works"
      (request :get "/environments")
      => (contains {:body {:environments ["env1" "env2"]}
                    :status 200})
      (provided
       (environments/environments) => {:env1 {}
                                       :env2 {}}))

(fact "that getting a list of in-progress deployments works"
      (request :get "/in-progress")
      => (contains {:body {:deployments ["deployment1" "deployment2"]}})
      (provided
       (deployments/in-progress) => ["deployment1" "deployment2"]))

(fact "that getting a list of paused deployments works"
      (request :get "/paused")
      => (contains {:body {:deployments ["deployment1" "deployment2"]}})
      (provided
       (deployments/paused) => ["deployment1" "deployment2"]))

(fact "that getting a list of deployments which are awaiting pause works"
      (request :get "/awaiting-pause")
      => (contains {:body {:deployments ["deployment1" "deployment2"]}})
      (provided
       (deployments/awaiting-pause) => ["deployment1" "deployment2"]))

(fact "that attempting to remove an in-progress deployment using an invalid application name gives a 400"
      (request :delete "/in-progress/with-hyphen/environment")
      => (contains {:status 400})
      (provided
       (environments/environments) => {:environment {}}))

(fact "that we can remove an in-progress deployment"
      (request :delete "/in-progress/application/environment")
      => (contains {:status 204})
      (provided
       (environments/environments) => {:environment {}}
       (redis/end-deployment {:application "application" :environment "environment" :region "eu-west-1"}) => anything))

(fact "that attempting to describe instances using an invalid application name gives a 400"
      (request :get "/describe-instances/with-hyphen/poke")
      => (contains {:status 400})
      (provided
       (environments/environments) => {:poke {}}))

(fact "that describe instances returns 200 text/plain when text/plain requested"
      (request :get "/describe-instances/ditto/poke" {:headers {"accept" "text/plain"}})
      => (contains {:status 200 :headers (contains {"Content-Type" "text/plain"})})
      (provided
       (environments/environments) => {:poke {}}
       (aws/describe-instances "poke" anything "ditto" nil) => ""))

(fact "that optional state param is passed to describe instances, response is json when not requested"
      (request :get "/describe-instances/ditto/poke" {:params {:state "stopped"}})
      => (contains {:status 200 :headers (contains {"Content-Type" "application/json; charset=utf-8"})})
      (provided
       (environments/environments) => {:poke {}}
       (aws/describe-instances "poke" anything "ditto" "stopped") => ""))

(fact "that getting our by-user deployment stats works"
      (request :get "/stats/deployments/by-user")
      => (contains {:body {:result {:stats "business"}}
                    :status 200})
      (provided
       (es/deployments-by-user) => {:stats "business"}))

(fact "that getting our by-user failed deployment stats works"
      (request :get "/stats/deployments/failed-by-user")
      => (contains {:body {:result {:stats "business"}}
                    :status 200})
      (provided
       (es/failed-deployments-by-user) => {:stats "business"}))

(fact "that getting our by-application deployment stats for a user works"
      (request :get "/stats/deployments/by-user/user/by-application")
      => (contains {:body {:result {:stats "business"}}
                    :status 200})
      (provided
       (es/deployments-by-user-by-application "user") => {:stats "business"}))

(fact "that getting our by-application deployment stats works"
      (request :get "/stats/deployments/by-application")
      => (contains {:body {:result {:stats "business"}}
                    :status 200})
      (provided
       (es/deployments-by-application) => {:stats "business"}))

(fact "that getting our by-year deployment stats works"
      (request :get "/stats/deployments/by-year")
      => (contains {:body {:result {:stats "business"}}
                    :status 200})
      (provided
       (es/deployments-by-year) => {:stats "business"}))

(fact "that getting our by-month deployment stats works"
      (request :get "/stats/deployments/by-month")
      => (contains {:body {:result {:stats "business"}}
                    :status 200})
      (provided
       (es/deployments-by-month) => {:stats "business"}))

(fact "that getting our by-day deployment stats works"
      (request :get "/stats/deployments/by-day")
      => (contains {:body {:result {:stats "business"}}
                    :status 200})
      (provided
       (es/deployments-by-day) => {:stats "business"}))

(fact "that attempting to get by-year and environment stats using an unknown environment gives a 400"
      (request :get "/stats/deployments/by-year/environment/unknown")
      => (contains {:status 400})
      (provided
       (environments/environments) => {}))

(fact "that getting our by-year and environment deployment stats works"
      (request :get "/stats/deployments/by-year/environment/something")
      => (contains {:body {:result {:stats "business"}}
                    :status 200})
      (provided
       (environments/environments) => {:something {}}
       (es/deployments-in-environment-by-year "something") => {:stats "business"}))

(fact "that attempting to get by-month and environment stats using an unknown environment gives a 400"
      (request :get "/stats/deployments/by-month/environment/unknown")
      => (contains {:status 400})
      (provided
       (environments/environments) => {}))

(fact "that getting our by-month and environment deployment stats works"
      (request :get "/stats/deployments/by-month/environment/something")
      => (contains {:body {:result {:stats "business"}}
                    :status 200})
      (provided
       (environments/environments) => {:something {}}
       (es/deployments-in-environment-by-month "something") => {:stats "business"}))

(fact "that attempting to get by-day and environment stats using an unknown environment gives a 400"
      (request :get "/stats/deployments/by-day/environment/unknown")
      => (contains {:status 400})
      (provided
       (environments/environments) => {}))

(fact "that getting our by-day and environment deployment stats works"
      (request :get "/stats/deployments/by-day/environment/something")
      => (contains {:body {:result {:stats "business"}}
                    :status 200})
      (provided
       (environments/environments) => {:something {}}
       (es/deployments-in-environment-by-day "something") => {:stats "business"}))
