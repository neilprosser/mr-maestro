(ns exploud.notification
  "## Deals with various notifications"
  (:require [clojure.tools.logging :refer [warn]]
            [environ.core :refer [env]]
            [exploud.log :as log]
            [postal.core :as mail]))

(def ^:private content-type
  "text/html; charset=\"UTF-8\"")

(defn- prod?
  [environment]
  (= :prod (keyword environment)))

(defn build-message-title
  "Creates a message title from parameters contained in the given deployment."
  [{:keys [application environment] :as deployment}]
  (format "%s: %s v%s deployed" (name environment) application (get-in deployment [:new-state :image-details :version])))

(defn build-message-body
  "Creates a message body from parameters contained in the given deployment."
  [{:keys [application id message user] :as deployment}]
  (let [image (get-in deployment [:new-state :image-details :id])
        version (get-in deployment [:new-state :image-details :version])]
    (format "<html>
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
        <td>%s</td>
      </tr>
      <tr>
        <td>Application:</td>
        <td>%s</td>
      </tr>
      <tr>
        <td>Version:</td>
        <td>%s</td>
      </tr>
      <tr>
        <td>Image:</td>
        <td>%s</td>
      </tr>
      <tr>
        <td>Message:</td>
        <td>%s</td>
      </tr>
      <tr>
        <td>Deployment details:</td>
        <td><a href=\"http://jeff.brislabs.com/exploud/#/deployments/%s\">%s</a></td>
      </tr>
  </body>
</html>" user application version image message id id)))

(defn- build-message
  [deployment]
  {:from (env :service-mail-from)
   :to (env :service-mail-to)
   :subject (build-message-title deployment)
   :body [{:type content-type :content (build-message-body deployment)}]})

(defn send-completion-message
  "Sends a 'deployment completed' email to the configured notification destination for the given deployment but only if there is something specified in `:service-smtp-host`."
  [{:keys [environment undo] :as deployment}]
  (when (and (not undo)
             (prod? environment))
    (let [host (env :service-smtp-host)]
      (when (seq host)
        (try
          (mail/send-message {:host host} (build-message deployment))
          (catch javax.mail.MessagingException e
            (log/write "Failed to send completion message.")
            (warn e "Failed to send completion message")))))))
