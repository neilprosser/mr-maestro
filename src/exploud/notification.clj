(ns exploud.notification
  "## Deals with various notifications"
  (:require [environ.core :refer [env]]
            [postal.core :as mail]))

(defn build-message-title
  "Creates a message title from parameters contained in the given deployment."
  [{:keys [ami application environment]}]
  (format "Entertainment Deployment: %s %s to %s" application ami environment))

(defn build-message-body
  "Creates a message body from parameters contained in the given deployment."
  [{:keys [ami application id message user]}]
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
        <td>Ami:</td>
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
</html>" user application ami message id id))

(defn send-completion-message
  "Sends a 'deployment completed' email to the configured notification destination for the given deployment but only if there is something specified in `:service-smtp-host`."
  [deployment]
  (when (seq (env :service-smtp-host))
    (let [host (env :service-smtp-host)
          from (env :service-mail-from)]
      (mail/send-message {:host host}
                         {:from from
                          :to (env :service-mail-to)
                          :subject (build-message-title deployment)
                          :body (build-message-body deployment)
                          :Content-Type "text/html; charset=\"UTF-8\""}))))
