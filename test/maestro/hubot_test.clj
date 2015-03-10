(ns maestro.hubot-test
  (:require [cheshire.core :as json]
            [maestro
             [environments :as environments]
             [http :as http]
             [hubot :refer :all]]
            [midje.sweet :refer :all]))

(fact "that making Hubot speak does what we want"
      (speak ["room"] ..message..) => nil
      (provided
       (json/generate-string {:rooms ["room"] :message ..message..}) => ..json..
       (http/simple-post "http://hubot/hubot/say-many" {:content-type :json
                                                        :body ..json..
                                                        :socket-timeout 5000}) => ..post-result..))

(fact "that making Hubot speak to `nil` rooms does nothing"
      (speak nil ..message..) => nil
      (provided
       (http/simple-post anything anything) => nil :times 0))

(fact "that making Hubot speak to `[]` rooms does nothing"
      (speak [] ..message..) => nil
      (provided
       (http/simple-post anything anything) => nil :times 0))

(fact "that making Hubot speak and getting an error doesn't blow-up"
      (speak ["room"] ..message..) => nil
      (provided
       (json/generate-string {:rooms ["room"] :message ..message..}) => ..json..
       (http/simple-post "http://hubot/hubot/say-many" anything) =throws=> (ex-info "Busted" {})))

(fact "that user-provided silence will be overridden if the environment should notify"
      (notify? "environment" true) => true
      (provided
       (environments/should-notify? "environment") => true))

(fact "that making Hubot speak about a deployment being started has the right message"
      (speak-about-deployment-start {:application "application"
                                     :environment "environment"
                                     :id "id"
                                     :message "Some message."
                                     :new-state {:image-details {:version "1.34"}
                                                 :onix {:rooms ["room"]}}
                                     :user "user"})
      => nil
      (provided
       (speak ["room"] "*user* is deploying *application* v1.34 to *environment*\n>>> Some message.\nhttp://maestro-ui/#/deployments/id") => nil))

(fact "that making Hubot speak about a deployment being started for an application with an additional channel, includes that channel"
      (speak-about-deployment-start {:application "application"
                                     :environment "environment"
                                     :id "id"
                                     :message "Some message."
                                     :new-state {:image-details {:version "1.34"}
                                                 :onix {:rooms ["room"]}}
                                     :user "user"})
      => nil
      (provided
       (speak ["room"] "*user* is deploying *application* v1.34 to *environment*\n>>> Some message.\nhttp://maestro-ui/#/deployments/id") => nil))

(fact "that making Hubot speak about a silent deployment being started does nothing"
      (speak-about-deployment-start {:silent true})
      => nil
      (provided
       (speak anything anything) => nil :times 0))

(fact "that making Hubot speak about a deployment which is a rollback has the right message"
      (speak-about-deployment-start {:application "application"
                                     :environment "environment"
                                     :id "id"
                                     :message "Some message."
                                     :new-state {:image-details {:version "1.34"}
                                                 :onix {:rooms ["room"]}}
                                     :rollback true
                                     :user "user"})
      => nil
      (provided
       (speak ["room"] "*user* is rolling back *application* to v1.34 in *environment*\n>>> Some message.\nhttp://maestro-ui/#/deployments/id") => nil))

(fact "that making Hubot speak about a silent deployment which is a rollback has the right message"
      (speak-about-deployment-start {:application "application"
                                     :environment "environment"
                                     :message "Some message."
                                     :new-state {:image-details {:version "1.34"}}
                                     :rollback true
                                     :silent true
                                     :user "user"})
      => nil
      (provided
       (speak anything anything) => nil :times 0))

(fact "that making Hubot speak about a deployment being undone has the right message"
      (speak-about-undo-start {:application "application"
                               :environment "environment"
                               :new-state {:image-details {:version "2.52"}
                                           :onix {:rooms ["room"]}}
                               :previous-state {:image-details {:version "2.51"}}
                               :silent true
                               :undo-message "Some undo message."
                               :undo-silent false
                               :undo-user "otheruser"
                               :user "user"})
      => nil
      (provided
       (speak ["room"] "*otheruser* is undoing deployment of *application* v2.52 in *environment* and replacing it with v2.51\n> Some undo message.") => nil))

(fact "that making Hubot speak about a deployment being undone with no previous state has the right message"
      (speak-about-undo-start {:application "application"
                               :environment "environment"
                               :new-state {:image-details {:version "2.52"}
                                           :onix {:rooms ["room"]}}
                               :silent true
                               :undo-message "Some undo message."
                               :undo-silent false
                               :undo-user "otheruser"
                               :user "user"})
      => nil
      (provided
       (speak ["room"] "*otheruser* is undoing deployment of *application* v2.52 in *environment*\n> Some undo message.") => nil))

(fact "that making Hubot speak about a silent undo doesn't do anything"
      (speak-about-undo-start {:undo-silent true})
      => nil
      (provided
       (speak anything anything) => nil :times 0))

(fact "that making Hubot speak about a deployment completion speaks to all rooms"
      (speak-about-deployment-completion {:application "application"
                                          :environment "environment"
                                          :id "id"
                                          :message "Some message."
                                          :new-state {:image-details {:version "2.52"}
                                                      :onix {:rooms ["room"]}}
                                          :user "user"})
      => nil
      (provided
       (speak ["room"] "*user* has deployed *application* v2.52 to *environment*") => nil
       (speak ["deployments"] "*user* has deployed *application* v2.52 to *environment*\n>>> Some message.\nhttp://maestro-ui/#/deployments/id") => nil))

(fact "that making Hubot speak about a silent deployment completion doesn't do anything"
      (speak-about-deployment-completion {:silent true})
      => nil
      (provided
       (speak anything anything) => nil :times 0))

(fact "that making Hubot speak about an undo completion speaks to all rooms"
      (speak-about-undo-completion {:application "application"
                                    :environment "environment"
                                    :new-state {:image-details {:version "2.52"}
                                                :onix {:rooms ["room"]}}
                                    :previous-state {:image-details {:version "2.51"}}
                                    :undo-message "Some undo message."
                                    :undo-user "undo-user"})
      => nil
      (provided
       (speak ["room"] "*undo-user* has undone the deployment of *application* v2.52 in *environment* and replaced it with v2.51") => nil
       (speak ["deployments"] "*undo-user* has undone the deployment of *application* v2.52 in *environment* and replaced it with v2.51\n> Some undo message.") => nil))

(fact "that making Hubot speak about a silent undo completion doesn't do anything"
      (speak-about-undo-completion {:undo-silent true})
      => nil
      (provided
       (speak anything anything) => nil :times 0))
