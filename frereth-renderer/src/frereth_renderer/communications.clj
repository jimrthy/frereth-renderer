(ns frereth-renderer.communications
  (:require [clojure.core.async :as async]
            [clojure.pprint :refer (pprint)]
            [com.stuartsierra.component :as component]
            [ribol.core :refer :all]
            [schema.core :as s]
            [schema.macros :as sm]
            [taoensso.timbre :as log]
            [zeromq.zmq :as mq])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defrecord Channels [ui uo cmd]
  component/Lifecycle
  (start
    [this]
    (into this {:ui (async/chan)
                :uo (async/chan)
                :cmd (async/chan)}))
  (stop
    [this]
    (doseq [c [ui uo cmd]]
      (when c
        (async/close! c)))
    (into this {:ui nil
                :uo nil
                :cmd nil})))

(defrecord Context [context]
  component/Lifecycle
  (start
    [this]
    (assoc this :context (mq/context 1)))
  (stop
    [this]
    (mq/close (:context this))
    (assoc this :context nil)))

(sm/defrecord URI [protocol :- s/Str
                  address :- s/Str
                  port :- s/Int]
  component/Lifecycle
  (start [this] this)
  (stop [this] this))
(declare build-url)

(sm/defrecord ClientUrl [uri :- URI]
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(sm/defrecord ClientSocket [context :- Context
                           socket
                           url :- ClientUrl]
  component/Lifecycle
  (start
   [this]
   (log/info "Connecting Client Socket to: "
             (with-out-str (pprint  url))
             ("\nout of:"
              (with-out-str (pprint this))))
   (try
     (let [sock (mq/socket (:context context) :dealer)
           url (build-url url)]
       (try
         (mq/connect sock)
         (catch RuntimeException ex
           (raise [:client-socket-connection-error
                   :reason ex])))
       (assoc this :socket sock))
     (catch RuntimeException ex
       (log/error ex)
       (raise [:client-socket-creation-failure
               {:reason ex}]))))
  (stop
   [this]
   (mq/disconnect socket (build-url url))
   (mq/set-linger socket 0)
   (mq/close socket)
   (assoc this :socket nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utilities

(defn client->ui-loop [reader-sock to-ui]
  ;; TODO: I really want to manage these exceptions right here, if at all possible.
  (raise-on [NullPointerException :zmq-npe
             Throwable :unknown]
            (loop []
              (let [msg (mq/receive reader-sock :dont-wait)]
                (when msg
                  (raise-on [Exception :async-fail]
                            (async/>!! to-ui msg)))
                (recur)))))

(defn client->ui [ctx to-ui]
  (raise :not-implemented)
  (manage
   (let [reader-sock (comment (mq/connected-socket ctx :dealer
                                                   "tcp://localhost:56568"))]
     (log/info "Entering Read Thread on socket " reader-sock)
     (client->ui-loop reader-sock to-ui))
   (on :zmq-npe [ex]
       ;; Debugging...what makes more sense instead?
       (escalate ex))))

(defn ui->client
  "Send user input messages to the client.
Over a 0mq socket created from ctx.
from-ui is the async channel where input events come from
cmd is an async channel that I was probably intending to use
for control messages.
Actually, it looks like I was planning on it being for
messages that come from the server"
  [ctx from-ui cmd]
  ;; Next address is totally arbitrary.
  ;; And, honestly, the client should probably be what does
  ;; the binding. Have to start somewhere.
  (raise :not-implemented)
  (let [writer-sock (comment (mq/connected-socket ctx :dealer
                                                  "tcp://127.0.0.1:56567"))]
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
  (raise :not-implemented)
  (let [writer-thread (async/thread (ui->client ctx from-ui cmd))
        reader-thread (async/thread (client->ui ctx to-ui))]
    [writer-thread reader-thread]))

(sm/defn build-url
  "This is pretty naive.
But works for my purposes"
  [{:keys [protocol address port] :as uri} :- URI]
  (str protocol "://" address ":" port))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(comment (defn start
           "N.B. dead-world is an atom.
TODO: formalize that using something like core.contract"
           [dead-world]
           ;; TODO: It actually might make some sort of sense to have multiple
           ;; threads involved here.
           (let [ctx (mq/context 1)
                 ;; Q: what kind of socket makes sense here?
                 ;; A: None, really. Since sockets aren't thread safe.
                                        ;socket (mq/socket ctx :router)
                 ui (async/chan)              ; user input -> client
                 uo (async/chan)              ; client -> graphics
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
                                 :terminator (async/chan)}))))

(comment (defn stop!
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
           live-world))

(defn new-channels
  []
  (map->Channels {}))

(defn new-client-socket
  []
  (map->ClientSocket {}))

(defn new-client-url
  [{:keys [client-protocol client-address client-port]}]
  (let [uri (strict-map->URI {:protocol client-protocol
                              :address client-address
                              :port client-port})]
    (strict-map->ClientUrl {:uri uri})))

(defn new-context
  []
  (map->Context {}))
