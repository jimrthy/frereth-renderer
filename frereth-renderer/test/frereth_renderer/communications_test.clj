(ns frereth-renderer.communications-test
  (:require  [cljeromq.core :as mq]
             [frereth-renderer.communications :as comms])
  (:use midje.sweet))

(facts "Basic handshake"
       (let [dead-comms (comms/init)
             live-comms (comm/start dead-comms)]
         (try
           (let [ctx (:context live-comms)
                 addr "tcp://127.0.0.1"
                 in-port 7842
                 url (format "%s:%d" addr in-port)]
             (mq/with-bound-socket [input ctx :router]
               :helo => (mq/recv input)
               (let [out-port (mq/recv input)
                     url (format "%s:%d" addr out-port)]
                 (mq/with-connected-socket [output ctx :req]
                   (mq/send output :PING)
                   :PONG => (mq/recv output)))))
           (finally
             (comms/stop live-comms)))))
