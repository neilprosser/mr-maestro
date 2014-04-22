(ns exploud.messages.notification-test
  (:require [environ.core :refer [env]]
            [exploud.messages.notification :refer :all]
            [exploud.notification :as n]
            [midje.sweet :refer :all]))

(def send-completion-notification-params
  {})

(fact "that sending a completion message works"
      (send-completion-notification {:parameters send-completion-notification-params}) => (contains {:status :success})
      (provided
       (n/send-completion-message send-completion-notification-params) => ..result..))

(fact "that getting an error while sending a completion notification is an error"
      (send-completion-notification {:parameters send-completion-notification-params}) => (contains {:status :error})
      (provided
       (n/send-completion-message send-completion-notification-params) =throws=> (ex-info "Busted" {})))
