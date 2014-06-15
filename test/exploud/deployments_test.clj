(ns exploud.deployments-test
  (:require [clj-time.core :as time]
            [exploud
             [actions :as actions]
             [deployments :refer :all]
             [elasticsearch :as es]
             [log :as log]
             [redis :as redis]]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "that asking whether Exploud is locked works"
      (locked?) => ..locked..
      (provided
       (redis/locked?) => ..locked..))

(fact "that locking Exploud works"
      (lock) => ..lock..
      (provided
       (redis/lock) => ..lock..))

(fact "that unlocking Exploud works"
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
       (redis/enqueue {:action :exploud.messages.data/start-deployment-preparation
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

(fact "that undoing a deployment which hasn't failed throws an exception"
      (undo undo-params) => (throws ExceptionInfo "Deployment has not failed")
      (provided
       (redis/in-progress? "application" "environment" "region") => "id"
       (es/deployment "id") => {:status "invalid"}))

(fact "that undoing a deployment triggers the right task"
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
       (redis/enqueue {:action :exploud.messages.data/start-deployment
                       :parameters {:id "id"
                                    :status "running"
                                    :undo true
                                    :undo-message "message"
                                    :undo-silent true
                                    :undo-user "user"}}) => ..enqueue-result..))

(def rollback-params
  {:application "application"
   :environment "environment"
   :id "id"
   :message "Rollback message"
   :region "region"
   :rollback true
   :user "user"})

(fact "that rolling back a deployment when a no suitable completed deployment exists throws an exception"
      (rollback rollback-params) => (throws ExceptionInfo "No previous completed deployment could be found")
      (provided
       (es/get-deployments {:application "application"
                            :environment "environment"
                            :from 0
                            :region "region"
                            :size 1
                            :status "completed"}) => []))

(fact "that rolling back a deployment with a suitable previous deployment does the right thing"
      (rollback rollback-params) => ..begin-result..
      (provided
       (es/get-deployments {:application "application"
                            :environment "environment"
                            :from 0
                            :region "region"
                            :size 1
                            :status "completed"})
       => [{:application "application"
            :environment "environment"
            :id "old-id"
            :previous-state {:hash "hash"
                             :image-details {:id "image"}}
            :region "region"
            :rollback true
            :status "completed"
            :user "original-user"}]
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
       (log/write* "id" "Resuming deployment") => anything
       (redis/enqueue {:action ..action.. :parameters ..deployment..}) => ..enqueue-result..
       (redis/resume "application" "environment" "region") => ..resume-result..))
