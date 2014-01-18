(ns frereth-renderer.communications
  (:require [cljeromq.core :as mq]
            [clojure.core.async :as async]
            [ribol.core :refer :all]
            [taoensso.timbre :as log])
  (:gen-class))

(defn client->ui-hand-shake
  "This is really the response part of the handshake that was initiated
sometime during ui->client."
  [sock]
  (let [msg (mq/recv sock)]
    ;; TODO: Really should verify that messages contents. For details like
    ;; protocol, version, etc.
    (mq/send sock :PONG)))

(defn client->ui-loop [reader-sock to-ui cmd]
  ;; TODO: I really want to manage these exceptions right here, if at all possible.
  (raise-on [NullPointerException :zmq-npe]
            [Throwable :unknown]
            (loop []
              ;; The vast majority of the time, this should be throwing
              ;; an Again exception.
              ;; Realistically, should be doing a Poll instead of a Read.
              ;; Or maybe a Read w/ a timeout.
              ;; Q: how's the language wrapper set up?
              (let [msg (mq/recv reader-sock (-> mq/const :control :dont-wait))]
                (when msg
                  (raise-on [Exception :async-fail]
                            ;; Here's at least one annoying bit about the
                            ;; way I've refactored this code into smaller
                            ;; pieces: I'm using >!! inside a go block
                            (async/>!! to-ui msg))))
              (recur))))  ; FIXME: Add a way to exit loop

(defn client->ui
  "Invoke thread that receives user input messages and forwards them to the client.
ctx is the 0mq messaging context
->ui is the core.async channel for submitting events
cmd is the command/control channel that lets us know when it's time to quit."
  [ctx ->ui cmd]
  ;; Subscribe to server events from the client
  (manage
   (let [reader-sock (mq/connected-socket ctx :rep
                                          ;; Arbitrary magic socket number that I
                                          ;; picked when I was setting up the client
                                          "tcp://localhost:7840")]
     (log/trace "Shaking hands with client")
     (client->ui-hand-shake reader-sock)
     (log/info "Entering Read Thread on socket " reader-sock)
     (client->ui-loop reader-sock ->ui cmd))
   (on :zmq-npe [ex]
       ;; Debugging...what makes more sense instead?
       (escalate ex))))

(defn ui->client [ctx from-ui cmd]
  ;; Push user events to the client to forward along to the server(s)
  ;; Using a REQ socket here seems at least a little dubious. The alternatives
  ;; that spring to mind seem worse. I send input to the Client over this channel,
  ;; it returns an ACK. Any real feedback comes over the client->ui socket.
  ;; This approach fails in that I really need to kill and recreate the socket
  ;; when the communication times out.
  ;; That approach fails in that I need to get *something* working before I
  ;; start worrying about things like error handling.
  (let [writer-sock (mq/connected-socket ctx :req
                                         "tcp://localhost:7842")]
    (log/debug "Pushing HELO to client")
    (mq/send writer-sock :helo)
    ;; This assumes send was blocking. Which it should have been,
    ;; but I've seen examples recently where it was not.
    (log/debug "Client accepted the HELO")
    (loop [to (async/timeout 60)] ; TODO: How long to wait?
      (let [[v ch] (async/alts!! [from-ui cmd to])]
        (condp = ch
          ;; TODO: Should probably look into processing
          ;; this message.
          from-ui (mq/send writer-sock v)
          cmd (throw (RuntimeException. "Received message over command channel.
What does this mean?"))
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
        reader-thread (async/thread (client->ui ctx to-ui cmd))]
    [writer-thread reader-thread]))

(defn init
  []
  (atom nil))

(defn start
  "N.B. dead-world is an atom.
TODO: formalize that using something like core.contract"
  [dead-world]
  ;; TODO: It actually might make some sort of sense to have multiple
  ;; threads involved here (instead of just 1).
  ;; Honestly, that doesn't seem very likely.
  (let [ctx (mq/context 1)
        ;; Seems obvious to start building sockets here.
        ;; That would be a horrible mistake.
        ;; Sockets aren't thread safe, and everything they're doing
        ;; really needs to happen in a background thread
        ui (async/chan) ; user input -> client (aka Input)
        uo (async/chan) ; client -> graphics (aka Output)
        command (async/chan) ; internal messaging (e.g. :exit)
        ;; Need an async channel that uses this socket
        ;; to communicate back and forth with the graphics namespace
        ;; to actually implement the UI.
        coupling (couple ctx ui uo command)]

    (reset! dead-world {:context ctx
                        ;; Output to Client
                        :user-input ui
                        ;; Input from Client
                        :command command
                        ;; Feedback to user
                        :user-output uo
                        ;; For notifying -main that the graphics loop is terminating
                        :terminator (async/chan)
                        ;; The channel that owns the thread
                        ;; where everything interesting is happening
                        :coupling coupling})))

(defn stop!
  [live-world]
  (if-let [ctx (:context live-world)]
    (do
      ;; This seems dubious...I'm probably missing something really
      ;; vital. I *think* I should write a :quit command here, then
      ;; read from :coupling to indicate that it's quit.
      ;; Then again, that isn't really anything but guess-work.
      (if-let [command-from-client (:command live-world)]
        (async/close! command-from-client)
        (log/warn "Missing command channel from client")))
      (if-let [coupling (:coupling live-world)]
              (if coupling
                (mq/close! socket)
                (log/error "Missing communications socket")))
      (mq/terminate! ctx)
      ;; Input from the user is really the least important part now
      ;; Seems wrong to wait this long to close it.
      (if-let [local-async (:user-input live-world)]
        (async/close! local-async)
        (log/warn "No local input channel...what happened?"))
    (log/error "Missing communications context"))
  live-world)
