(ns exploud.notification-test
  (:require [environ.core :refer [env]]
            [exploud.notification :refer :all]
            [midje.sweet :refer :all]
            [postal.core :as mail]))

(def deployment {:ami "ami-1234abcd"
                 :application "myapp"
                 :environment "prod"
                 :id "some-id"
                 :message "Some message"
                 :user "auser"
                 :version "0.12"})

(fact "that the body is created correctly"
      (build-message-body deployment)
      => "<html>
  <head>
    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">
    <style type=\"text/css\">
      table { border-collapse: collapse; }
      table td { padding: 0.5em; }
    </style>
  </head>
  <body>
    <table>
      <tr>
        <td>User:</td>
        <td>auser</td>
      </tr>
      <tr>
        <td>Application:</td>
        <td>myapp</td>
      </tr>
      <tr>
        <td>Version:</td>
        <td>0.12</td>
      </tr>
      <tr>
        <td>Ami:</td>
        <td>ami-1234abcd</td>
      </tr>
      <tr>
        <td>Message:</td>
        <td>Some message</td>
      </tr>
      <tr>
        <td>Deployment details:</td>
        <td><a href=\"http://jeff.brislabs.com/exploud/#/deployments/some-id\">some-id</a></td>
      </tr>
  </body>
</html>")

(fact "that messages are correctly formatted for sending"
      (send-completion-message deployment) => nil
      (provided
       (env :service-smtp-host) => "dummy-value"
       (env :service-mail-from) => "noreply@brislabs.com"
       (env :service-mail-to) => "to@address.com"
       (build-message-body deployment) => "body"
       (mail/send-message {:host "dummy-value"}
                          {:from "noreply@brislabs.com"
                           :to "to@address.com"
                           :subject "prod: myapp v0.12 deployed"
                           :body [{:type "text/html; charset=\"UTF-8\""
                                   :content "body"}]})
       => nil))

(fact "that we don't send when the environment is something other than `:prod`"
      (send-completion-message (assoc deployment :environment :something-else))
      => nil)
