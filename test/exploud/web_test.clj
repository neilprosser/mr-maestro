(ns exploud.web-test
  (:require [cheshire.core :as json]
            [clj-time.format :as fmt]
            [exploud
             [aws :as aws]
             [deployments :as deployments]
             [elasticsearch :as es]
             [info :as info]
             [web :refer :all]]
            [midje.sweet :refer :all]))

(defn request
  "Creates a compojure request map and applies it to our application.
   Accepts method, resource and optionally an extended map"
  [method resource & [{:keys [params headers]
                       :or {:params {}
                            :headers {}}}]]
  (let [{:keys [body] :as res} (app {:request-method method
                                     :uri resource
                                     :params params
                                     :headers headers})]
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
      (request :get "/1.x/deployments" {:params {:size "0"}})
      => (contains {:status 400}))

(fact "that getting deployments with a positive size works"
      (request :get "/1.x/deployments" {:params {:size "12"}})
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
      (request :get "/1.x/deployments" {:params {:size "adsdsads"}})
      => (contains {:status 400}))

(fact "that getting deployments with a negative size gives a 400"
      (request :get "/1.x/deployments" {:params {:size "-12"}})
      => (contains {:status 400}))

(fact "that getting deployments with a zero from works"
      (request :get "/1.x/deployments" {:params {:from "0"}})
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
      (request :get "/1.x/deployments" {:params {:from "12"}})
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
      (request :get "/1.x/deployments" {:params {:start-to "not a date"}})
      => (contains {:status 400}))

(fact "that getting deployments with a valid full works"
      (request :get "/1.x/deployments" {:params {:full "true"}})
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
      (request :get "/1.x/deployments" {:params {:full "fdasdsdas"}})
      => (contains {:status 400}))

(fact "that getting deployment logs without a since date passes nil to the underlying function"
      (:body (request :get "/1.x/deployments/id/logs" {:params {}}))
      => {:logs [{:a "log"}]}
      (provided
       (es/deployment-logs "id" nil) => [{:a "log"}]))

(fact "that getting deployment logs with an invalid since date returns 400"
      (request :get "/1.x/deployments/id/logs" {:params {:since "garbage"}})
      => (contains {:status 400}))

(fact "that getting deployment logs with a valid since date does the right thing"
      (:body (request :get "/1.x/deployments/id/logs" {:params {:since "2003-01-04T00:02:01Z"}}))
      => {:logs [{:a "log"}]}
      (provided
       (fmt/parse "2003-01-04T00:02:01Z") => ..date..
       (es/deployment-logs "id" ..date..) => [{:a "log"}]))

(fact "that creating an application with an illegal name returns 400."
      (request :put "/1.x/applications/my-application")
      => (contains {:status 400})
      (provided
       (deployments/locked?) => false))

(fact "that creating an application with a legal name returns 201."
      (request :put "/1.x/applications/myapplication")
      => (contains {:status 201})
      (provided
       (deployments/locked?) => false
       (info/upsert-application anything "myapplication" anything) => {}))

(fact-group :unit :describe-instances
            (fact "that describe instances returns 200 text/plain when text/plain requested"
                  (request :get "/1.x/describe-instances/ditto/poke" {:headers {"accept" "text/plain"}})
                  => (contains {:status 200 :headers (contains {"Content-Type" "text/plain"})})
                  (provided
                   (aws/describe-instances "poke" anything "ditto" nil) => ""))

            (fact "that optional state param is passed to describe instances, response is json when not requested"
                  (request :get "/1.x/describe-instances/ditto/poke" {:params {:state "stopped"}})
                  => (contains {:status 200 :headers (contains {"Content-Type" "application/json; charset=utf-8"})})
                  (provided
                   (aws/describe-instances "poke" anything "ditto" "stopped") => "")))
