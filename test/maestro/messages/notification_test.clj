(ns maestro.messages.notification-test
  (:require [environ.core :refer [env]]
            [maestro.hubot :as hubot]
            [maestro.messages.notification :refer :all]
            [midje.sweet :refer :all]))

(fact "that sending a start notification for a normal deployment works"
      (send-start-notification {:parameters {}}) => (contains {:status :success})
      (provided
       (hubot/speak-about-deployment-start {}) => ..result..))

(fact "that sending a start notification for an undo works"
      (send-start-notification {:parameters {:undo true}}) => (contains {:status :success})
      (provided
       (hubot/speak-about-undo-start {:undo true}) => ..result..))

(fact "that getting an error while sending a start notification is an error"
      (send-start-notification {:parameters {}}) => (contains {:status :error})
      (provided
       (hubot/speak-about-deployment-start {}) =throws=> (ex-info "Busted" {})))

(fact "that sending a completion notification for a normal deployment works"
      (send-completion-notification {:parameters {}}) => (contains {:status :success})
      (provided
       (hubot/speak-about-deployment-completion {}) => ..result..))

(fact "that sending a completion notification for an undo works"
      (send-completion-notification {:parameters {:undo true}}) => (contains {:status :success})
      (provided
       (hubot/speak-about-undo-completion {:undo true}) => ..result..))

(fact "that getting an error while sending a completion notification is an error"
      (send-completion-notification {:parameters {}}) => (contains {:status :error})
      (provided
       (hubot/speak-about-deployment-completion {}) =throws=> (ex-info "Busted" {})))
