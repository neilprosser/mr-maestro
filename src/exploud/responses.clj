(ns exploud.responses)

(defn error-with
  [e]
  {:status :error
   :throwable e})

(defn retry
  []
  {:status :retry})

(defn retry-after
  [millis]
  {:status :retry
   :backoff-ms millis})

(defn capped-retry-after
  [millis attempt max-attempts]
  (if (<= max-attempts attempt)
    (error-with (ex-info "Maximum number of attempts has been reached." {}))
    (retry-after millis)))

(defn success
  [parameters]
  {:status :success
   :parameters parameters})
