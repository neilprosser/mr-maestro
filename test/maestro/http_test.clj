(ns maestro.http-test
  (:require [clj-http.client :as http]
            [maestro.http :refer :all]
            [midje.sweet :refer :all])
  (:import clojure.lang.ExceptionInfo))


(fact "that merging with our default params does what we expect"
      (merge-with-default-params {:business :joy}) => {:throw-exceptions false
                                                       :conn-timeout 5000
                                                       :socket-timeout 15000
                                                       :business :joy})

(fact "that performing a GET does what we want"
      (simple-get ..url.. {:extra :params})
      => ..response..
      (provided
       (merge-with-default-params {:extra :params}) => ..params..
       (http/get ..url.. ..params..) => ..response..))

(fact "that getting a ConnectException when performing a GET converts it to an ExceptionInfo"
      (simple-get ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/get ..url.. anything) =throws=> (java.net.ConnectException. "Busted")))

(fact "that getting a ConnectTimeoutException when performing a GET converts it to an ExceptionInfo"
      (simple-get ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/get ..url.. anything) =throws=> (org.apache.http.conn.ConnectTimeoutException. "Busted")))

(fact "that getting a SocketTimeoutException when performing a GET converts it to an ExceptionInfo"
      (simple-get ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/get ..url.. anything) =throws=> (java.net.SocketTimeoutException. "Busted")))

(fact "that getting a UnknownHostException when performing a GET converts it to an ExceptionInfo"
      (simple-get ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/get ..url.. anything) =throws=> (java.net.UnknownHostException. "Busted")))

(fact "that performing a POST does what we want"
      (simple-post ..url.. {:extra :params})
      => ..response..
      (provided
       (merge-with-default-params {:extra :params}) => ..params..
       (http/post ..url.. ..params..) => ..response..))

(fact "that getting a ConnectException when performing a POST converts it to an ExceptionInfo"
      (simple-post ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/post ..url.. anything) =throws=> (java.net.ConnectException. "Busted")))

(fact "that getting a ConnectTimeoutException when performing a POST converts it to an ExceptionInfo"
      (simple-post ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/post ..url.. anything) =throws=> (org.apache.http.conn.ConnectTimeoutException. "Busted")))

(fact "that getting a SocketTimeoutException when performing a POST converts it to an ExceptionInfo"
      (simple-post ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/post ..url.. anything) =throws=> (java.net.SocketTimeoutException. "Busted")))

(fact "that getting a UnknownHostException when performing a POST converts it to an ExceptionInfo"
      (simple-post ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/post ..url.. anything) =throws=> (java.net.UnknownHostException. "Busted")))

(fact "that performing a PUT does what we want"
      (simple-put ..url.. {:extra :params})
      => ..response..
      (provided
       (merge-with-default-params {:extra :params}) => ..params..
       (http/put ..url.. ..params..) => ..response..))

(fact "that getting a ConnectException when performing a PUT converts it to an ExceptionInfo"
      (simple-put ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/put ..url.. anything) =throws=> (java.net.ConnectException. "Busted")))

(fact "that getting a ConnectTimeoutException when performing a PUT converts it to an ExceptionInfo"
      (simple-put ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/put ..url.. anything) =throws=> (org.apache.http.conn.ConnectTimeoutException. "Busted")))

(fact "that getting a SocketTimeoutException when performing a PUT converts it to an ExceptionInfo"
      (simple-put ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/put ..url.. anything) =throws=> (java.net.SocketTimeoutException. "Busted")))

(fact "that getting a UnknownHostException when performing a PUT converts it to an ExceptionInfo"
      (simple-put ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/put ..url.. anything) =throws=> (java.net.UnknownHostException. "Busted")))

(fact "that performing a DELETE does what we want"
      (simple-delete ..url.. {:extra :params})
      => ..response..
      (provided
       (merge-with-default-params {:extra :params}) => ..params..
       (http/delete ..url.. ..params..) => ..response..))

(fact "that getting a ConnectException when performing a DELETE converts it to an ExceptionInfo"
      (simple-delete ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/delete ..url.. anything) =throws=> (java.net.ConnectException. "Busted")))

(fact "that getting a ConnectTimeoutException when performing a DELETE converts it to an ExceptionInfo"
      (simple-delete ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/delete ..url.. anything) =throws=> (org.apache.http.conn.ConnectTimeoutException. "Busted")))

(fact "that getting a SocketTimeoutException when performing a DELETE converts it to an ExceptionInfo"
      (simple-delete ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/delete ..url.. anything) =throws=> (java.net.SocketTimeoutException. "Busted")))

(fact "that getting a UnknownHostException when performing a DELETE converts it to an ExceptionInfo"
      (simple-delete ..url..)
      => (throws ExceptionInfo "Busted")
      (provided
       (http/delete ..url.. anything) =throws=> (java.net.UnknownHostException. "Busted")))
