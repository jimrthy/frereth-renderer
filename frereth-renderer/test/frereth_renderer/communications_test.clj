(ns frereth-renderer.communications-test
  (:require  [cljeromq.core :as mq]
             [frereth-renderer.communications :as comms]
             [taoensso.timbre :as log])
  (:use midje.sweet))

(facts "Basic handshake"
       (let [dead-comms (comms/init)
             live-comms (comms/start dead-comms)]
         (try
           (log/info "TEST: Communications system initialized")
           ;; It's at least a little tempting to set up my own context
           ;; here, just to try to make the test a little more
           ;; realistic.
           ;; But I'm really not too worried about the basic 0mq functionality.
           (let [ctx (:context live-comms)
                 addr "tcp://127.0.0.1"
                 in-port 7842
                 url (format "%s:%d" addr in-port)]
             (log/info "Basic handshake TEST: Starting to communicate w/ background thread")
             (try
               (mq/with-bound-socket [input ctx :router url]
                 (log/info "TEST client is Receiving on " url)
                 ;; TODO: Really should set timeouts
                 ;; FIXME: remote-id isn't a string. Don't use recv here.
                 (let [remote-id (mq/raw-recv input)]
                   (mq/recv-more? input) => truthy
                   (let [empty-frame (mq/recv input)]
                     (seq? empty-frame) => falsey
                     (mq/recv input) => :helo
                     (log/info "TEST: Received the HELO")
                     (mq/recv-more? input) => truthy
                     (let [out-port (mq/recv input)
                           url (format "%s:%d" addr out-port)]
                       (mq/recv-more? input) => falsey
                       ;; I'm getting here
                       (log/info "Pinging output on port " out-port)
                       (mq/with-connected-socket [output ctx :req]
                         (mq/send output :PING)
                         ;; But not here.
                         (log/info "Handshake test: PING sent, awaiting PONG")
                         ;; TODO: Timeout!
                         (mq/recv output) => :PONG
                         (mq/recv-more? output) => falsey
                         (log/info "Handshake test: All is well!"))))))
               (catch Exception ex
                 (log/error "Handshake TEST FAIL: " ex)
                 (throw))))
           (finally
             (log/info "Basic handshake TEST: complete. Clean up communications system")
             (comms/stop! live-comms)
             (log/info "Basic handshake TEST: Communications system stopped")))))
