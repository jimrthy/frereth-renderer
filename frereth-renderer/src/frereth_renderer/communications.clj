(ns frereth-renderer.communications
  (:require [cljeromq.core :as mq]
            [clojure.core.async :as async]
            [taoensso.timbre :as timbre
             :refer [trace info debug warn error fatal spy with-log-level]])
  (:gen-class))

(defn proxy
  "Need to read from both ui and socket.
When a message comes in on socket, forward it to command
When a message comes in on ui, forward it to socket.

This gets at least vaguely interesting because I want to send comms both
ways. The obvious easy implementation is to have them be synchronous.

That simply does not work well in any sort of realistic scenario.

This should be low-hanging fruit, but it's actually looking like some pretty hefty
meat."
  [from-ui to-ui from-client to-client]
  ;; Ah, well. This is what I have time to tackle.
  (comment (let msg ["Don't actually start here: there are other pieces that need to be fixed first.
But this is pretty high up on the priority list"]
                (throw (RuntimeException. msg))))
  ;; Q: What makes sense?
  ;; A: My first answer is a couple of threads that ignore each other on the core/async side.
  ;; This is almost guaranteed to be a spectacular failure. I probably need at least 2 sockets
  ;; for the to/from conversation with the Client.
  ;; Sockets are not thread-safe. Absolutely cannot [ab]use them this way without crashes.
  (let [controller (atom False)
        command (async/thread
                 (fn []
                   (throw (RuntimeException. "Command reader (from client)"))
                   (when @controller (recur))))
        ui (async/thread
            (fn []
              (throw (RuntimeException. "UI Writer (to client)"))))]
    [command ui controller]))

(defn init
  []
  (atom nil))

(defn start
  "N.B. dead-world is an atom.
TODO: formalize that using something like core.contract"
  [dead-world]
  ;; TODO: It actually might make some sort of sense to have multiple
  ;; threads involved here.
  (let [ctx (mq/context 1)
        ;; TODO: what kind of socket makes sense here?
        socket (mq/socket ctx :router)
        ui (async/chan)
        command (async/chan)]
    ;; FIXME: Do I want into, merge, or something totally different?
    ;; FIXME: Whichever. Need an async channel that uses this socket
    ;; to communicate back and forth with the graphics namespace
    ;; to actually implement the UI.
    (proxy ui command socket)

    (reset! dead-world {:context ctx
                        :socket socket
                        ;; Output to Client
                        :user-input ui
                        ;; Input from Client
                        :command command
                        ;; For notifying -main that the graphics loop is terminating
                        :terminator (async/chan)})))

(defn stop!
  [live-world]
  (if-let [local-async (:user-input live-world)]
    (async/close! local-async)
    (warn "No local input channel...what happened?"))
  (if-let [command-from-client (:command live-world)]
    (async/close! command-from-client)
    (warn "Missing command channel from client"))
  (let [ctx (:context live-world)
        socket (:socket live-world)]
    ;; FIXME: Realistically, both these situations should throw an exception
    (if socket
      (mq/close! socket)
      (error "Missing communications socket"))
    (if ctx
      (mq/terminate! ctx)
      (error "Missing communications context")))
  live-world)
