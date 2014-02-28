(ns exploud.stats
  (:refer-clojure :exclude [sort find])
  (:require [clj-time.core :as time]
            [monger
             [collection :as mc]
             [joda-time]
             [operators :refer :all]
             [query :refer :all]]))

(defn deployments-collection
  []
  "deployments")

(defn deployments-by-user
  []
  (let [result (mc/aggregate (deployments-collection) [{$match {$and [{:user {$exists true}}
                                                                      {:user {$ne ""}}]}}
                                                       {$group {:_id {:user "$user"}
                                                                :count {$sum 1}}}])]
    (map (fn [r] {:user (get-in r [:_id :user])
                 :count (get-in r [:count])}) result)))

(defn deployments-by-application
  []
  (let [result (mc/aggregate (deployments-collection) [{$match {$and [{:application {$exists true}}
                                                                      {:application {$ne ""}}]}}
                                                       {$group {:_id {:application "$application"}
                                                                :count {$sum 1}}}])]
    (map (fn [r] {:application (get-in r [:_id :application])
                 :count (get-in r [:count])}) result)))

(defn deployments-by-month
  []
  (let [result (mc/aggregate (deployments-collection) [{$match {$and [{:application {$exists true}}
                                                                      {:application {$ne ""}}
                                                                      {:start {$exists true}}]}}
                                                       {$group {:_id {:year {$year "$start"}
                                                                      :month {$month "$start"}}
                                                                :count {$sum 1}}}])]
    (map (fn [r] {:date (time/date-time (get-in r [:_id :year]) (get-in r [:_id :month]))
                 :count (get-in r [:count])}) result)))

(defn deployments-in-environment-by-month
  [environment]
  (let [result (mc/aggregate (deployments-collection) [{$match {$and [{:environment environment}
                                                                      {:start {$exists true}}]}}
                                                       {$group {:_id {:year {$year "$start"}
                                                                      :month {$month "$start"}}
                                                                :count {$sum 1}}}])]
    (prn result)
    (map (fn [r] {:date (time/date-time (get-in r [:_id :year]) (get-in r [:_id :month]))
                 :count (get-in r [:count])}) result)))

(defn deployments-by-month-and-application
  []
  (let [result (mc/aggregate (deployments-collection) [{$match {$and [{:application {$exists true}}
                                                                      {:application {$ne ""}}
                                                                      {:start {$exists true}}]}}
                                                       {$group {:_id {:application "$application"
                                                                      :year {$year "$start"}
                                                                      :month {$month "$start"}}
                                                                :count {$sum 1}}}])]
    (map (fn [r] {:application (get-in r [:_id :application])
                 :date (time/date-time (get-in r [:_id :year]) (get-in r [:_id :month]))
                 :count (get-in r [:count])}) result)))

(defn deployments-of-application-by-month
  [application]
  (let [result (mc/aggregate (deployments-collection) [{$match {$and [{:application application}
                                                                      {:start {$exists true}}]}}
                                                       {$group {:_id {:year {$year "$start"}
                                                                      :month {$month "$start"}}
                                                                :count {$sum 1}}}])]
    (map (fn [r] {:date (time/date-time (get-in r [:_id :year]) (get-in r [:_id :month]))
                 :count (get-in r [:count])}) result)))
