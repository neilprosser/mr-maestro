(ns exploud.hubot-test
  (:require [cheshire.core :as json]
            [exploud
             [http :as http]
             [hubot :refer :all]]
            [midje.sweet :refer :all]))

(fact "that making Hubot speak does what we want"
      (speak ..room.. ..message..) => nil
      (provided
       (json/generate-string {:room ..room.. :message ..message..}) => ..json..
       (http/simple-post "http://hubot:8080/hubot/say" {:content-type :json
                                                        :body ..json..
                                                        :socket-timeout 5000}) => ..post-result..))

(fact "that making Hubot speak and getting an error doesn't blow-up"
      (speak ..room.. ..message..) => nil
      (provided
       (json/generate-string {:room ..room.. :message ..message..}) => ..json..
       (http/simple-post "http://hubot:8080/hubot/say" anything) =throws=> (ex-info "Busted" {})))

(fact "that making Hubot speak about a deployment being started has the right message"
      (speak-about-deployment-start {:application "application"
                                     :environment "environment"
                                     :message "Some message."
                                     :new-state {:image-details {:id "image-id"
                                                                 :version "1.34"}}
                                     :user "user"})
      => nil
      (provided
       (speak "503594" "user is deploying application v1.34 (image-id) to environment. Some message.") => nil))

(fact "that making Hubot speak about a silent deployment being started does nothing"
      (speak-about-deployment-start {:application "application"
                                     :environment "environment"
                                     :message "Some message."
                                     :new-state {:image-details {:id "image-id"
                                                                 :version "1.34"}}
                                     :silent true
                                     :user "user"})
      => nil
      (provided
       (speak anything anything) => nil :times 0))

(fact "that making Hubot speak about a deployment which is a rollback has the right message"
      (speak-about-deployment-start {:application "application"
                                     :environment "environment"
                                     :message "Some message."
                                     :new-state {:image-details {:id "image-id"
                                                                 :version "1.34"}}
                                     :rollback true
                                     :user "user"})
      => nil
      (provided
       (speak "503594" "user is rolling back application to v1.34 (image-id) in environment. Some message.") => nil))

(fact "that making Hubot speak about a silent deployment which is a rollback has the right message"
      (speak-about-deployment-start {:application "application"
                                     :environment "environment"
                                     :message "Some message."
                                     :new-state {:image-details {:id "image-id"
                                                                 :version "1.34"}}
                                     :rollback true
                                     :silent true
                                     :user "user"})
      => nil
      (provided
       (speak anything anything) => nil :times 0))

(fact "that making Hubot speak about a deployment being undone has the right message"
      (speak-about-deployment-undo {:application "application"
                                    :environment "environment"
                                    :new-state {:image-details {:id "new-image-id"
                                                                :version "2.52"}}
                                    :previous-state {:image-details {:id "old-image-id"
                                                                     :version "2.51"}}
                                    :undo-message "Some undo message."
                                    :user "user"})
      => nil
      (provided
       (speak "503594" "user is undoing deployment of application v2.52 (new-image-id) in environment and replacing it with v2.51 (old-image-id). Some undo message.") => nil))

(fact "that making Hubot speak about a deployment being undone with no previous state has the right message"
      (speak-about-deployment-undo {:application "application"
                                    :environment "environment"
                                    :new-state {:image-details {:id "new-image-id"
                                                                :version "2.52"}}
                                    :undo-message "Some undo message."
                                    :user "user"})
      => nil
      (provided
       (speak "503594" "user is undoing deployment of application v2.52 (new-image-id) in environment. Some undo message.") => nil))

(fact "that making Hubot speak about a silent deployment being undone has the right message"
      (speak-about-deployment-undo {:application "application"
                                    :environment "environment"
                                    :new-state {:image-details {:id "new-image-id"
                                                                :version "2.52"}}
                                    :previous-state {:image-details {:id "old-image-id"
                                                                     :version "2.51"}}
                                    :silent true
                                    :undo-message "Some undo message."
                                    :user "user"})
      => nil
      (provided
       (speak anything anything) => nil :times 0))
