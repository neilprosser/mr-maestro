(ns maestro.deployments-test
  (:require [clj-time.core :as time]
            [maestro
             [actions :as actions]
             [deployments :refer :all]
             [elasticsearch :as es]
             [log :as log]
             [redis :as redis]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "that asking whether Maestro is locked works"
      (locked?) => ..locked..
      (provided
       (redis/locked?) => ..locked..))

(fact "that locking Maestro works"
      (lock) => ..lock..
      (provided
       (redis/lock) => ..lock..))

(fact "that unlocking Maestro works"
      (unlock) => ..unlock..
      (provided
       (redis/unlock) => ..unlock..))

(fact "that asking whether a deployment is paused works"
      (paused? {:application "application" :environment "environment" :region "region"}) => ..result..
      (provided
       (redis/paused? "application" "environment" "region") => ..result..))

(fact "that getting the list of paused deployments works"
      (paused) => ..result..
      (provided
       (redis/paused) => ..result..))

(fact "that registering a pause works"
      (register-pause {:application "application" :environment "environment" :region "region"}) => ..result..
      (provided
       (redis/register-pause "application" "environment" "region") => ..result..))

(fact "that unregistering a pause works"
      (unregister-pause {:application "application" :environment "environment" :region "region"}) => ..result..
      (provided
       (redis/unregister-pause "application" "environment" "region") => ..result..))

(fact "that asking whether a pause is registered works"
      (pause-registered? {:application "application" :environment "environment" :region "region"}) => ..result..
      (provided
       (redis/pause-registered? "application" "environment" "region") => ..result..))

(fact "that getting the list of deployments awaiting a pause works"
      (awaiting-pause) => ..result..
      (provided
       (redis/awaiting-pause) => ..result..))

(fact "that registering a cancellation works"
      (register-cancel {:application "application" :environment "environment" :region "region"}) => ..result..
      (provided
       (redis/register-cancel "application" "environment" "region") => ..result..))

(fact "that unregistering a cancellation works"
      (unregister-cancel {:application "application" :environment "environment" :region "region"}) => ..result..
      (provided
       (redis/unregister-cancel "application" "environment" "region") => ..result..))

(fact "that asking whether a cancellation is registered works"
      (cancel-registered? {:application "application" :environment "environment" :region "region"}) => ..result..
      (provided
       (redis/cancel-registered? "application" "environment" "region") => ..result..))

(fact "that getting the list of deployments awaiting a cancellation works"
      (awaiting-cancel) => ..result..
      (provided
       (redis/awaiting-cancel) => ..result..))

(def in-progress-params
  {:application "application"
   :environment "environment"
   :region "region"})

(fact "that determining whether a deployment is in-progress works"
      (in-progress? in-progress-params) => ..result..
      (provided
       (redis/in-progress? "application" "environment" "region") => ..result..))

(fact "that getting the in progress deployments works"
      (in-progress) => ..in-progress..
      (provided
       (redis/in-progress) => ..in-progress..))

(fact "that a deployment isn't stopped on a retryable task if there isn't a last failed deployment"
      (stopped-on-retryable-task? {:application "application"
                                   :environment "environment"
                                   :region "region"})
      => falsey
      (provided
       (es/last-failed-deployment {:application "application"
                                   :environment "environment"
                                   :region "region"})
       => nil))

(fact "that a valid task isn't retryable task when it isn't failed"
      (stopped-on-retryable-task? {:application "application"
                                   :environment "environment"
                                   :region "region"})
      => falsey
      (provided
       (es/last-failed-deployment {:application "application"
                                   :environment "environment"
                                   :region "region"})
       => {:id "deployment-id"}
       (es/deployment-tasks "deployment-id")
       => [{:action :not-this-one
            :status "completed"}
           {:action :maestro.messages.health/wait-for-instances-to-be-healthy
            :status "running"}]))

(fact "that waiting for instances to exist is a retryable task when it has failed"
      (stopped-on-retryable-task? {:application "application"
                                   :environment "environment"
                                   :region "region"})
      => truthy
      (provided
       (es/last-failed-deployment {:application "application"
                                   :environment "environment"
                                   :region "region"})
       => {:id "deployment-id"}
       (es/deployment-tasks "deployment-id")
       => [{:action :not-this-one
            :status "completed"}
           {:action :maestro.messages.asg/wait-for-instances-to-exist
            :status "failed"}]))

(fact "that waiting for instances to be in service is a retryable task when it has failed"
      (stopped-on-retryable-task? {:application "application"
                                   :environment "environment"
                                   :region "region"})
      => truthy
      (provided
       (es/last-failed-deployment {:application "application"
                                   :environment "environment"
                                   :region "region"})
       => {:id "deployment-id"}
       (es/deployment-tasks "deployment-id")
       => [{:action :not-this-one
            :status "completed"}
           {:action :maestro.messages.asg/wait-for-instances-to-be-in-service
            :status "failed"}]))

(fact "that waiting for instances to be healthy is a retryable task when it has failed"
      (stopped-on-retryable-task? {:application "application"
                                   :environment "environment"
                                   :region "region"})
      => truthy
      (provided
       (es/last-failed-deployment {:application "application"
                                   :environment "environment"
                                   :region "region"})
       => {:id "deployment-id"}
       (es/deployment-tasks "deployment-id")
       => [{:action :not-this-one
            :status "completed"}
           {:action :maestro.messages.health/wait-for-instances-to-be-healthy
            :status "failed"}]))

(fact "that waiting for load balancers to be healthy is a retryable task when it has failed"
      (stopped-on-retryable-task? {:application "application"
                                   :environment "environment"
                                   :region "region"})
      => truthy
      (provided
       (es/last-failed-deployment {:application "application"
                                   :environment "environment"
                                   :region "region"})
       => {:id "deployment-id"}
       (es/deployment-tasks "deployment-id")
       => [{:action :not-this-one
            :status "completed"}
           {:action :maestro.messages.health/wait-for-load-balancers-to-be-healthy
            :status "failed"}]))

(fact "that waiting for the old ASG to be deleted is a retryable task when it has failed"
      (stopped-on-retryable-task? {:application "application"
                                   :environment "environment"
                                   :region "region"})
      => truthy
      (provided
       (es/last-failed-deployment {:application "application"
                                   :environment "environment"
                                   :region "region"})
       => {:id "deployment-id"}
       (es/deployment-tasks "deployment-id")
       => [{:action :not-this-one
            :status "completed"}
           {:action :maestro.messages.asg/wait-for-old-auto-scaling-group-deletion
            :status "failed"}]))

(fact "that another action isn't a retryable task"
      (stopped-on-retryable-task? {:application "application"
                                   :environment "environment"
                                   :region "region"})
      => falsey
      (provided
       (es/last-failed-deployment {:application "application"
                                   :environment "environment"
                                   :region "region"})
       => {:id "deployment-id"}
       (es/deployment-tasks "deployment-id")
       => [{:action :not-retryable
            :status "failed"}]))

(def can-retry-parameters
  {:application "application"
   :environment "environment"
   :region "region"})

(fact "that a deployment cannot be retried when nothing is in progress"
      (can-retry? can-retry-parameters) => falsey
      (provided
       (in-progress? can-retry-parameters) => false))

(fact "that a deployment cannot be retried when it is paused"
      (can-retry? can-retry-parameters) => falsey
      (provided
       (in-progress? can-retry-parameters) => true
       (paused? can-retry-parameters) => true))

(fact "that a deployment cannot be retried when it isn't stopped on a retryable task"
      (can-retry? can-retry-parameters) => falsey
      (provided
       (in-progress? can-retry-parameters) => true
       (paused? can-retry-parameters) => false
       (stopped-on-retryable-task? can-retry-parameters) => false))

(def begin-params
  {:application "application"
   :environment "environment"
   :id "id"
   :region "region"})

(fact "that beginning a deployment does what we expect when it doesn't exist"
      (begin begin-params) => begin-params
      (provided
       (time/now) => ..start..
       (redis/begin-deployment begin-params) => true
       (es/upsert-deployment "id" {:application "application"
                                   :environment "environment"
                                   :id "id"
                                   :start ..start..
                                   :status "running"
                                   :region "region"}) => ..es-result..
       (redis/enqueue {:action :maestro.messages.data/start-deployment-preparation
                       :parameters {:application "application"
                                    :environment "environment"
                                    :id "id"
                                    :start ..start..
                                    :status "running"
                                    :region "region"}}) => ..enqueue-result..))

(fact "that beginning a deployment does what we expect when one already exists"
      (begin begin-params) => (throws ExceptionInfo "Deployment already in progress")
      (provided
       (redis/begin-deployment begin-params) => false))

(def end-params
  {:application "application"
   :environment "environment"
   :region "region"})

(fact "that ending a deployment does what we expect"
      (end end-params) => end-params
      (provided
       (redis/end-deployment end-params) => ..result..))

(def cancel-params
  {:application "application"
   :environment "environment"
   :region "region"})

(fact "that cancelling a deployment does what we expect"
      (cancel cancel-params) => cancel-params
      (provided
       (redis/cancel-deployment cancel-params) => ..result..))

(def undo-params
  {:application "application"
   :environment "environment"
   :message "message"
   :region "region"
   :silent true
   :user "user"})

(fact "that undoing a deployment which isn't already in progress throws an exception"
      (undo undo-params) => (throws ExceptionInfo "Deployment is not in progress")
      (provided
       (redis/in-progress? "application" "environment" "region") => nil))

(fact "that undoing a deployment which can't be found throws an exception"
      (undo undo-params) => (throws ExceptionInfo "Deployment could not be found")
      (provided
       (redis/in-progress? "application" "environment" "region") => "id"
       (es/deployment "id") => nil))

(fact "that undoing a deployment which hasn't failed and isn't paused throws an exception"
      (undo undo-params) => (throws ExceptionInfo "Deployment has not failed or is not paused")
      (provided
       (redis/in-progress? "application" "environment" "region") => "id"
       (redis/paused? "application" "environment" "region") => false
       (es/deployment "id") => {:application "application"
                                :environment "environment"
                                :region "region"
                                :status "invalid"}))

(fact "that undoing a deployment which is paused triggers the right task"
      (undo undo-params) => "id"
      (provided
       (redis/in-progress? "application" "environment" "region") => "id"
       (redis/paused? "application" "environment" "region") => true
       (es/deployment "id") => {:application "application"
                                :environment "environment"
                                :id "id"
                                :region "region"
                                :status "running"}
       (es/upsert-deployment "id" {:application "application"
                                   :environment "environment"
                                   :id "id"
                                   :region "region"
                                   :status "running"
                                   :undo true
                                   :undo-message "message"
                                   :undo-silent true
                                   :undo-user "user"}) => ..es-result..
       (redis/resume "application" "environment" "region") => ..resume-result..
       (redis/enqueue {:action :maestro.messages.data/start-deployment
                       :parameters {:application "application"
                                    :environment "environment"
                                    :id "id"
                                    :region "region"
                                    :status "running"
                                    :undo true
                                    :undo-message "message"
                                    :undo-silent true
                                    :undo-user "user"}}) => ..enqueue-result..))

(fact "that undoing a failed deployment triggers the right task"
      (undo undo-params) => "id"
      (provided
       (redis/in-progress? "application" "environment" "region") => "id"
       (es/deployment "id") => {:id "id"
                                :status "failed"}
       (es/upsert-deployment "id" {:id "id"
                                   :status "running"
                                   :undo true
                                   :undo-message "message"
                                   :undo-silent true
                                   :undo-user "user"}) => ..es-result..
       (redis/resume "application" "environment" "region") => ..resume-result..
       (redis/enqueue {:action :maestro.messages.data/start-deployment
                       :parameters {:id "id"
                                    :status "running"
                                    :undo true
                                    :undo-message "message"
                                    :undo-silent true
                                    :undo-user "user"}}) => ..enqueue-result..))

(def redeploy-params
  {:application "application"
   :environment "environment"
   :id "id"
   :message "Redeploy message"
   :region "region"
   :user "user"})

(fact "that redeploying an application when no suitable completed deployment exists throws an exception"
      (redeploy redeploy-params) => (throws ExceptionInfo "No previous completed deployment could be found")
      (provided
       (es/last-completed-deployment {:application "application"
                                      :environment "environment"
                                      :region "region"}) => nil))

(fact "that redeploying an application with a suitable previous deployment does the right thing"
      (redeploy redeploy-params) => ..begin-result..
      (provided
       (es/last-completed-deployment {:application "application"
                                      :environment "environment"
                                      :region "region"})
       => {:application "application"
           :environment "environment"
           :id "old-id"
           :new-state {:hash "old-hash"
                       :image-details {:id "image"}}
           :region "region"
           :status "completed"
           :user "original-user"}
       (begin {:application "application"
               :environment "environment"
               :id "id"
               :message "Redeploy message"
               :new-state {:image-details {:id "image"}}
               :region "region"
               :user "user"}) => ..begin-result..))

(def rollback-params
  {:application "application"
   :environment "environment"
   :id "id"
   :message "Rollback message"
   :region "region"
   :rollback true
   :user "user"})

(fact "that rolling back a deployment when no suitable completed deployment exists throws an exception"
      (rollback rollback-params) => (throws ExceptionInfo "No previous completed deployment could be found")
      (provided
       (es/last-completed-deployment {:application "application"
                                      :environment "environment"
                                      :region "region"}) => nil))

(fact "that rolling back a deployment with a suitable previous deployment does the right thing"
      (rollback rollback-params) => ..begin-result..
      (provided
       (es/last-completed-deployment {:application "application"
                                      :environment "environment"
                                      :region "region"})
       => {:application "application"
           :environment "environment"
           :id "old-id"
           :previous-state {:hash "hash"
                            :image-details {:id "image"}}
           :region "region"
           :rollback true
           :status "completed"
           :user "original-user"}
       (begin {:application "application"
               :environment "environment"
               :id "id"
               :message "Rollback message"
               :new-state {:hash "hash"
                           :image-details {:id "image"}}
               :region "region"
               :rollback true
               :user "user"}) => ..begin-result..))

(fact "that pausing a deployment works"
      (pause {:application "application" :environment "environment" :id "id" :region "region"}) => ..pause-result..
      (provided
       (redis/unregister-pause "application" "environment" "region") => ..unregister-pause-result..
       (redis/pause "application" "environment" "id" "region") => ..pause-result..))

(fact "that resuming a deployment which isn't paused does nothing"
      (resume {:application "application" :environment "environment" :region "region"}) => nil
      (provided
       (redis/in-progress? "application" "environment" "region") => nil))

(fact "that resuming a deployment which is paused does all the right things"
      (resume {:application "application" :environment "environment" :region "region"}) => ..resume-result..
      (provided
       (redis/in-progress? "application" "environment" "region") => "id"
       (es/deployment "id") => ..deployment..
       (es/deployment-tasks "id") => ..tasks..
       (actions/resume-action ..tasks..) => ..action..
       (log/write* "id" "Resuming deployment.") => anything
       (redis/enqueue {:action ..action.. :parameters ..deployment..}) => ..enqueue-result..
       (redis/resume "application" "environment" "region") => ..resume-result..))

(fact "that retrying a deployment throws up when the deployment isn't in progress"
      (retry {:application "application" :environment "environment" :region "region"}) => (throws ExceptionInfo "Deployment is not in progress")
      (provided
       (redis/in-progress? "application" "environment" "region") => nil))

(fact "that retrying a deployment throws up whe the deployment cannot be found"
      (retry {:application "application" :environment "environment" :region "region"}) => (throws ExceptionInfo "Deployment could not be found")
      (provided
       (redis/in-progress? "application" "environment" "region") => "id"
       (es/deployment "id") => nil))

(fact "that retrying a deployment throws up when we can't find the last task"
      (retry {:application "application" :environment "environment" :region "region"}) => (throws ExceptionInfo "Unable to find last task")
      (provided
       (redis/in-progress? "application" "environment" "region") => "id"
       (es/deployment "id") => {:id "id" :status "failed"}
       (es/deployment-tasks "id") => []))

(fact "that retrying a deployment kicks off the last task"
      (retry {:application "application" :environment "environment" :region "region"}) => ..enqueue-result..
      (provided
       (redis/in-progress? "application" "environment" "region") => "id"
       (es/deployment "id") => {:id "id" :status "failed"}
       (es/deployment-tasks "id") => [{:action :not-this-one} {:action :some-action}]
       (log/write* "id" "Retrying deployment.") => nil
       (es/upsert-deployment "id" {:id "id" :status "running"}) => ..upsert-result..
       (redis/enqueue {:action :some-action
                       :parameters {:id "id" :status "running"}}) => ..enqueue-result..))
