(ns frereth-renderer.core
  (:require [frereth-renderer.graphics :as graphics]
            [frereth-renderer.system :as sys]
            [taoensso.timbre :as timbre
             :refer [trace debug info warn error fatal spy with-log-level]])
  (:gen-class))

(comment (defn fsm
           "OK, so it isn't exactly an impressive state machine.
Especially since I have an entire FSM namespace now.
FIXME: This needs to just go away."
           [universe]
           (trace "Initializing State Machine")
           (let [sock @(:client-socket universe)]
             ;; TODO: Show a splash screen
             (trace "Really should be showing a splash screen")
             ;; I'm drawing something. It's just blank.

             (mq/send sock :ready-to-draw :dont-wait)
             (trace "Waiting for Response from Client")
             (let [response (mq/recv sock :wait)]
               ;; TODO: Move on to do something interesting.
               (trace (str response))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [dead-world (sys/init)
        world (sys/start dead-world)]
    (try
      (comment (fsm world))
      ;; Wait for this to exit.
      ;; This approach seems horribly wrong.
      ;; Honestly, the client should tell it to exit when it sends a
      ;; close message.
      ;; Except that it needs to be able to shut down even (or maybe
      ;; especially) if the client goes away.
      ;; Probably shouldn't be running it like this in the first
      ;; place. Except...what are the better options?
      (.join @(:front-end world))
      ;; Then clean up
      ;; I'm getting here almost instantly. Meaning that front-end
      ;; apparently isn't getting set to anything that seems reasonable.
      (finally (sys/stop world)))))
