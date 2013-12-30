(ns frereth-renderer.communications
  (:require [cljeromq.core :as mq]
            [clojure.core.async :as async]
            [ribol.core :refer :all]
            [taoensso.timbre :as log])
  (:gen-class))

(defn client->ui-hand-shake
  "Establish that the client is waiting to send commands/status to us"
  [sock]
  ;; Don't want to do anything fancy yet, but it's a REP socket.
  ;; Have to notify it that I'm here.
  (mq/send sock :PING)
  )

(defn client->ui-loop [reader-sock to-ui]
  ;; TODO: I really want to manage these exceptions right here, if at all possible.
  (raise-on [NullPointerException :zmq-npe]
            [Throwable :unknown]
            (loop []
              (let [msg (mq/recv reader-sock (-> mq/const :control :dont-wait))]
                (when msg
                  (raise-on [Exception :async-fail]
                            ;; Here's at least one annoying bit about the
                            ;; way I've refactored this code into smaller
                            ;; pieces: I'm using >!! inside a go block
                            (async/>!! to-ui msg))))
              (recur))))  ; FIXME: Add a way to exit loop

(defn client->ui [ctx to-ui]
  ;; Subscribe to server events from the client
  (manage
   (let [reader-sock (mq/connected-socket ctx :sub
                                          ;; Arbitrary magic socket number that I
                                          ;; picked when I was setting up the client
                                          "tcp://localhost:7840")]
     (log/trace "Shaking hands with client")
     (client->ui-hand-shake reader-sock)
     (log/info "Entering Read Thread on socket " reader-sock)
     (client->ui-loop reader-sock to-ui))
   (on :zmq-npe [ex]
       ;; Debugging...what makes more sense instead?
       (escalate ex))))

(defn ui->client [ctx from-ui cmd]
  ;; Push user events to the client to forward along to the server(s)
  (let [writer-sock (mq/connected-socket ctx :push
                                         "tcp://localhost:7842")]
    (loop [to (async/timeout 60)] ; TODO: How long to wait?
      (let [[v ch] (async/alts!! [from-ui cmd to])]
        (condp = ch
          ;; TODO: Should probably look into processing
          ;; this message.
          from-ui (mq/send writer-sock v)
          cmd (throw (RuntimeException. "What does this mean?"))
          to
          ;; TODO: Check some flag to determine whether we're done.
          (recur (async/timeout 60)))))))

(defn couple
  "Need to read from both ui and socket.
When a message comes in on socket, forward it to command
When a message comes in on ui, forward it to socket.

This gets at least vaguely interesting because I want to send comms both
ways. The obvious easy implementation is to have them be synchronous.

That simply does not work well in any sort of realistic scenario.

This should be low-hanging fruit, but it's actually looking like some pretty hefty
meat."
  [ctx from-ui to-ui cmd]
  (let [writer-thread (async/thread (ui->client ctx from-ui cmd))
        reader-thread (async/thread (client->ui ctx to-ui))]
    [writer-thread reader-thread]))

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
        ;; Q: what kind of socket makes sense here?
        ;; A: None, really. Since sockets aren't thread safe.
        ;socket (mq/socket ctx :router)
        ui (async/chan) ; user input -> client
        uo (async/chan) ; client -> graphics
        command (async/chan)]
    ;; FIXME: Do I want into, merge, or something totally different?
    ;; FIXME: Whichever. Need an async channel that uses this socket
    ;; to communicate back and forth with the graphics namespace
    ;; to actually implement the UI.
    (couple ctx ui uo command)

    (reset! dead-world {:context ctx
                        ;; Output to Client
                        :user-input ui
                        ;; Input from Client
                        :command command
                        ;; Feedback to user
                        :user-output uo
                        ;; For notifying -main that the graphics loop is terminating
                        :terminator (async/chan)})))

(defn stop!
  [live-world]
  (if-let [local-async (:user-input live-world)]
    (async/close! local-async)
    (log/warn "No local input channel...what happened?"))
  (if-let [command-from-client (:command live-world)]
    (async/close! command-from-client)
    (log/warn "Missing command channel from client"))
  (let [ctx (:context live-world)
        socket (:socket live-world)]
    ;; FIXME: Realistically, both these situations should throw an exception
    (if socket
      (mq/close! socket)
      (log/error "Missing communications socket"))
    (if ctx
      (mq/terminate! ctx)
      (log/error "Missing communications context")))
  live-world)
