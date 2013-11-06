(ns exploud.web_test
  (:require [cheshire.core :as json]
            [clj-time.format :as fmt]
            [exploud
             [store :as store]
             [info :as info]
             [web :refer :all]]
            [midje.sweet :refer :all]))

(defn request
  "Creates a compojure request map and applies it to our application.
   Accepts method, resource and optionally an extended map"
  [method resource & [{:keys [params]
                       :or {:params {}}}]]
  (let [{:keys [body] :as res} (app {:request-method method
                                     :uri resource
                                     :params params})]
    (cond-> res
            (instance? java.io.InputStream body)
            (assoc :body (json/parse-string (slurp body) true)))))

(fact "that ping pongs"
      (:body (request :get "/1.x/ping"))
      => "pong")

(fact "that getting deployments with no parameters works"
      (request :get "/1.x/deployments")
      => (contains {:body {:deployments []}})
      (provided
       (store/get-deployments {:application nil
                               :start-from nil
                               :start-to nil
                               :size nil
                               :from nil})
       => []))

(fact "that getting deployments with a zero size gives a 400"
      (request :get "/1.x/deployments" {:params {:size "0"}})
      => (contains {:status 400}))

(fact "that getting deployments with a positive size works"
      (request :get "/1.x/deployments" {:params {:size "12"}})
      => (contains {:body {:deployments []}})
      (provided
       (store/get-deployments {:application nil
                               :start-from nil
                               :start-to nil
                               :size 12
                               :from nil})
      => []))

(fact "that getting deployments with a non-integer size gives a 400"
      (request :get "/1.x/deployments" {:params {:size "adsdsads"}})
      => (contains {:status 400}))

(fact "that getting deployments with a negative size gives a 400"
      (request :get "/1.x/deployments" {:params {:size "-12"}})
      => (contains {:status 400}))

(fact "that getting deployments with a zero from works"
      (request :get "/1.x/deployments" {:params {:from "0"}})
      => (contains {:body {:deployments []}})
      (provided
       (store/get-deployments {:application nil
                               :start-from nil
                               :start-to nil
                               :size nil
                               :from 0})
      => []))

(fact "that getting deployments with a positive from works"
      (request :get "/1.x/deployments" {:params {:from "12"}})
      => (contains {:body {:deployments []}})
      (provided
       (store/get-deployments {:application nil
                               :start-from nil
                               :start-to nil
                               :size nil
                               :from 12})
      => []))

(fact "that getting deployments with a non-integer from gives a 400"
      (request :get "/1.x/deployments" {:params {:from "adsdsads"}})
      => (contains {:status 400}))

(fact "that getting deployments with a negative from gives a 400"
      (request :get "/1.x/deployments" {:params {:from "-12"}})
      => (contains {:status 400}))

(fact "that getting deployments with a valid start-from works"
      (request :get "/1.x/deployments" {:params {:start-from "2013-10-10"}})
      => (contains {:body {:deployments []}})
      (provided
       (fmt/parse "2013-10-10")
       => ..date..
       (fmt/parse nil)
       => nil
       (store/get-deployments {:application nil
                               :start-from ..date..
                               :start-to nil
                               :size nil
                               :from nil})
       => []))

(fact "that getting deployments with an invalid start-from gives a 400"
      (request :get "/1.x/deployments" {:params {:start-from "not a date"}})
      => (contains {:status 400}))

(fact "that getting deployments with a valid start-to works"
      (request :get "/1.x/deployments" {:params {:start-to "2013-10-10"}})
      => (contains {:body {:deployments []}})
      (provided
       (fmt/parse "2013-10-10")
       => ..date..
       (fmt/parse nil)
       => nil
       (store/get-deployments {:application nil
                               :start-from nil
                               :start-to ..date..
                               :size nil
                               :from nil})
       => []))

(fact "that getting deployments with an invalid start-to gives a 400"
      (request :get "/1.x/deployments" {:params {:start-to "not a date"}})
      => (contains {:status 400}))

(fact "that getting instances for a given app works"
      (request :get "/1.x/instances/myapp")
      => (contains {:status 200})
      (provided
       (info/instances-for-application anything "myapp") => '()))

(fact "That getting active amis for a given app works."
      (request :get "/1.x/images/myapp")
      => (contains {:status 200})
      (provided
       (info/active-amis-for-app anything "myapp") => '()))

(fact "that creating an application with an illegal name returns 400."
      (request :put "/1.x/applications/my-application")
      => (contains {:status 400}))

(fact "that creating an application with a legal name returns 201."
      (request :put "/1.x/applications/myapplication")
      => (contains {:status 201})
      (provided
       (info/upsert-application anything "myapplication" anything) => {}))
