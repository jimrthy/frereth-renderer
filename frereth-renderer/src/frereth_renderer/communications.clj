(ns frereth-renderer.communications
  (:require [cljeromq.constants :as mqk]
            [cljeromq.core :as mq]
            [clojure.core.async :as async]
            [ribol.core :refer :all]
            [taoensso.timbre :as log])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn client->ui-hand-shake
  "This is really the response part of the handshake that was initiated
sometime during ui->client."
  [sock]
  (let [msg (mq/recv sock)]
    ;; TODO: Really should verify that messages contents. For details like
    ;; protocol, version, etc.
    (mq/send sock :PONG)))

(defn client->ui-loop [reader-sock ->ui cmd]
  ;; TODO: I really want to manage these exceptions right here, if at all possible.
  (raise-on [NullPointerException :zmq-npe]
            [Throwable :unknown]
            (loop []
              ;; The vast majority of the time, this should be throwing
              ;; an Again exception.
              ;; Realistically, should be doing a Poll instead of a Read.
              ;; Or maybe a Read w/ a timeout.
              ;; Q: how's the language wrapper set up?
              (let [msg (mq/recv reader-sock (-> mqk/const :control :dont-wait))]
                (when msg
                  (raise-on [Exception :async-fail]
                            ;; Here's at least one annoying bit about the
                            ;; way I've refactored this code into smaller
                            ;; pieces: I'm using >!! inside a go block
                            (async/>!! ->ui msg))))
              (recur))))  ; FIXME: Add a way to exit loop

(defn client->ui
  "Invoke thread that receives user input messages and forwards them to the client.
ctx is the 0mq messaging context
->ui is the core.async channel for submitting events
cmd is the command/control channel that lets us know when it's time to quit."
  [ctx ->ui cmd]
  ;; Subscribe to server events from the client
  (manage
   ;; TODO: Allow binding to external addresses. Though we should really
   ;; reject a client coming in from the wrong place. That really gets
   ;; into encrypted sockets and white-list auth.
   ;; Of course, it's also for remote clients/renderers. Which is definitely
   ;; a "future version" feature.
   (mq/with-randomly-bound-socket [reader-sock port
                                   ctx :rep
                                   "tcp://127.0.0.1"]
     ;; Let the UI thread know which port this is listening on.
     (async/>!! cmd port)
     (log/trace "Shaking hands with client")
     (client->ui-hand-shake reader-sock)
     (log/info "Entering Read Thread on socket " ->ui)
     (client->ui-loop reader-sock ->ui cmd))
   (on :zmq-npe [ex]
       ;; Debugging...what makes more sense instead?
       (escalate ex))))

(defn ui->client-loop
  "Read messages from the actual UI and forward them to the Client.
ui-> is the async socket to receive those messages.
cmd is the async command channel used to pass control messages into this thread.
->client is the 0mq socket to the client."
  [ui-> cmd ->client]
  (loop [to (async/timeout 60)] ; TODO: How long to wait?
    (let [[v ch] (async/alts!! [ui-> cmd to])]
      (condp = ch
        ;; TODO: Should probably look into processing
        ;; this message.
        ui-> (do (mq/send ->client v)
                    (let [ack (mq/recv ->client)]
                      (log/debug ack)))
        cmd (do
              (log/debug v)
              ;; The most obvious possibility here is the command to exit.
              ;; Q: Do I want to put a 'done' flag in an atom to exit?
              (throw
               (RuntimeException. "Received message over command channel.
Need to handle this.")))
        to nil
        ;; TODO: Check some flag to determine whether we're done.
        ;; That should really be signalled by cmd returning nil.
        ;; So, honestly, we don't care about this timeout.
        ))
    (recur (async/timeout 60))))

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
                                         "tcp://127.0.0.1:7842")]
    (let [reader-port (async/<!! cmd)]
      (log/debug "Pushing HELO to client")
      (mq/send-partial writer-sock :helo)
      ;; Trigger the client to connect to the socket bound in the
      ;; client->ui thread
      (mq/send writer-sock reader-port)
      (log/debug "Client accepted the HELO")
      ;; Wait for the other side of the hand-shake
      (let [ack (mq/recv writer-sock)]
        (log/debug (str "Handshake:\n" ack)))
      (ui->client-loop from-ui cmd writer-sock))))

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
        reader-thread (async/thread (client->ui ctx to-ui cmd))
        ;; The timing could get very finicky on this, if there
        ;; are other threads involved.
        ;; Actually, that would be pretty disastrous.
        ;; N.B. This absolutely must be single-threaded!!
        reader-port (async/<!! cmd)]
    {:ui->client writer-thread
     :client->ui reader-thread
     :->client-port reader-port}))

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
  (if-let [command-from-client (:command live-world)]
    (do
      ;; Do this, to stop both the i/o threads
      (async/>!! command-from-client :exit)
      (async/>!! command-from-client :exit)
      ;; Though it would be cleaner to just close this channel and
      ;; make them smart enough to understand what that means.
      ;; TODO: Make that work.
      (async/close! command-from-client))
    (log/warn "Missing command channel from client"))

  (if-let [in-async (:user-input live-world)]
    (async/close! in-async)
    (log/warn "No local input channel...what happened?"))
  (if-let [out-async (:user-output live-world)]
    (async/close! out-async)
    (log/warn "No local output channel...where'd it go?"))

  ;; Actually, just closing those channelss should kill off
  ;; everything else.
  (if-let [coupling (:coupling live-world)]
    (log/warn "How do I decouple everything?")
    (log/error "Missing communications coupling"))

  ;; Do need to make sure the sockets are all closed first.
  ;; Which seems to mean joining the i/o threads in :coupling
  (if-let [ctx (:context live-world)]
    (do
      ;; This seems dubious...I'm probably missing something really
      ;; vital. I *think* I should write a :quit command here, then
      ;; read from :coupling to indicate that it's quit.
      ;; Then again, that isn't really anything but guess-work.
      (mq/terminate! ctx))
    (log/error "Missing communications context"))

  (if-let [terminator (:terminator live-world)]
    (async/>!! :exiting)
    (log/warn "Missing terminator channel. This is bad!"))

  ;; Should really return a map of the moldering remains.
  ;; Q: But why bother?
  (init))
