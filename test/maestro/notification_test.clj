(ns maestro.notification-test
  (:require [environ.core :refer [env]]
            [maestro
             [environments :as environments]
             [log :as log]
             [notification :refer :all]]
            [midje.sweet :refer :all]
            [postal.core :as mail]))

(def deployment {:application "myapp"
                 :environment "prod"
                 :id "some-id"
                 :message "Some message"
                 :new-state {:image-details {:id "ami-1234abcd"
                                             :version "0.12"}}
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
        <td>Version:</td>
        <td>0.12</td>
      </tr>
      <tr>
        <td>Image:</td>
        <td>ami-1234abcd</td>
      </tr>
      <tr>
        <td>Message:</td>
        <td>Some message</td>
      </tr>
      <tr>
        <td>Deployment details:</td>
        <td><a href=\"http://maestro-ui/#/deployments/some-id\">some-id</a></td>
      </tr>
  </body>
</html>")

(fact "that messages are correctly formatted for sending"
      (send-completion-message deployment) => nil
      (provided
       (environments/should-notify? "prod") => true
       (env :mail-smtp-host) => "dummy-value"
       (env :mail-from-address) => "noreply@brislabs.com"
       (env :mail-to-address) => "to@address.com"
       (build-message-body deployment) => "body"
       (mail/send-message {:host "dummy-value"}
                          {:from "noreply@brislabs.com"
                           :to "to@address.com"
                           :subject "prod: myapp v0.12 deployed"
                           :body [{:type "text/html; charset=\"UTF-8\""
                                   :content "body"}]})
       => nil))

(fact "that we don't send when we're not told to"
      (send-completion-message deployment)
      => nil
      (provided
       (environments/should-notify? "prod") => false
       (mail/send-message anything anything) => nil :times 0))

(fact "that we don't send when we're told to and it's an undo"
      (send-completion-message (assoc deployment :undo true))
      => nil
      (provided
       (mail/send-message anything anything) => nil :times 0))

(fact "that an exception while sending the completion message doesn't screw things up"
      (send-completion-message deployment)
      => nil
      (provided
       (environments/should-notify? "prod") => true
       (env :mail-smtp-host) => "dummy-value"
       (env :mail-from-address) => "noreply@brislabs.com"
       (env :mail-to-address) => "to@address.com"
       (build-message-body deployment) => "body"
       (mail/send-message {:host "dummy-value"}
                          {:from "noreply@brislabs.com"
                           :to "to@address.com"
                           :subject "prod: myapp v0.12 deployed"
                           :body [{:type "text/html; charset=\"UTF-8\""
                                   :content "body"}]})
       =throws=> (javax.mail.MessagingException. "Boom")
       (log/write "Failed to send completion message.") => nil))
