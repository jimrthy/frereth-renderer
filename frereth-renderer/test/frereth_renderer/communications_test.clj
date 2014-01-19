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
           (let [ctx (:context live-comms)
                 addr "tcp://127.0.0.1"
                 in-port 7842
                 url (format "%s:%d" addr in-port)]
             (log/info "Basic handshake TEST: Starting to communicate w/ background thread")
             (mq/with-bound-socket [input ctx :router]
               (log/info "Test client is Receiving")
               ;; TODO: Really should set a timeout
               :helo => (mq/recv input)
               (log/info "TEST: Received the HELO")
               (let [out-port (mq/recv input)
                     url (format "%s:%d" addr out-port)]
                 (log/info "Pinging output on port " out-port)
                 (mq/with-connected-socket [output ctx :req]
                   (mq/send output :PING)
                   (log/info "Handshake test: PING sent, awaiting PONG")
                   ;; TODO: Timeout!
                   :PONG => (mq/recv output)
                   (log/info "Handshake test: All is well!")))))
           (finally
             (log/info "Basic handshake test: complete. Clean up communications system")
             (comms/stop! live-comms)
             (log/info "Basic handshake test: Communications system stopped")))))
