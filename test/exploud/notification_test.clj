(ns exploud.notification_test
  (:require [environ.core :refer [env]]
            [exploud.notification :refer :all]
            [midje.sweet :refer :all]
            [postal.core :as mail]))

(def deployment {:ami "ami-1234abcd"
                 :application "myapp"
                 :environment "test"
                 :id "some-id"
                 :message "Some message"
                 :user "auser"})

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
                           :subject "Entertainment Deployment: myapp ami-1234abcd to test"
                           :body "body"
                           :Content-Type "text/html; charset=\"UTF-8\""})
       => nil))
