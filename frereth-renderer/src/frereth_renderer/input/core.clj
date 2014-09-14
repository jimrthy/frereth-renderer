(ns frereth-renderer.input.core
  (:require [clojure.core.async :as async]
            [clojure.pprint :refer (pprint)]
            [com.stuartsierra.component :as component]
            [frereth-renderer.fsm :as fsm]
            [ribol.core :refer (raise)]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(declare begin-communications)
;;; It looks like I'd specifically intended this to cope
;;; with messages coming in from a Client. Which, honestly,
;;; doesn't seem very useful by themselves.
;;; N.B. client-channel should be started elsewhere, since
;;; we don't do anything but read from it.
(defrecord ClientConnectionThread
    [channels fsm go-block]
  component/Lifecycle
  (start [this]
    (log/debug "Starting communications thread")
    (if channels
      (assoc this :go-block (begin-communications this))
      (do
        (log/warn "Missing communications channels")
        (raise [:misconfiguration
                {:missing "Async Channel from Client"}]))))
  (stop [this]
    (into this {:control-channel nil})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(defn begin-communications
  " Actually updating things isn't as interesting [at first] or [quite]
as vital as the eye candy...but it's a very close second in both
categories."
  [component]
  (try
    (log/debug "Trying to start the background
communications dispatcher thread\nState:\n"
               (with-out-str (pprint component)))
    (let [stopper (-> component :channels :terminator)
          client-channel (-> component :channels :uo)
          fsm (:fsm component)
          communications-thread
          (async/go
           (try
             (loop [[c msg] (async/alts! client-channel stopper)]
               ;; Check for channel closed
               (when-not (nil? msg)
                 (log/trace "Control Message:\n" msg)
                 (assert (= c client-channel))

                 ;; This approach misses quite a few points, I think.
                 ;; This pieces of the FSM should be for very coarsely-
                 ;; grained transitions...
                 ;; then again, maybe my entire picture of the architecture
                 ;; is horribly flawed.
                 ;; TODO: Where's the actual communication happening?
                 ;; All I really care about right here, right now is
                 ;; establishing the heartbeat connection to and from
                 ;; the client(s).
                 ;; Which, really, seems more than a little silly.

                 (log/info "Communications Loop Received\n" 
                           msg "\nfrom client channel")

                 ;; This really isn't good enough. This also has to handle responses
                 ;; to UI requests. I'm torn between using yet another channel
                 ;; (to where?) for this and using penumbra's message queue.
                 ;; Then again, maybe requiring client apps to work with the
                 ;; FSM makes sense...except that now we're really talking about
                 ;; a multiple secondary FSMs which I have absolutely no control
                 ;; over. That doesn't exactly seem like a good recipe for a
                 ;; "happy path"       
                 (let [next-state (fsm/send-transition fsm msg true)]
                   ;; TODO: I don't think this is really even all that close
                   ;; to what I want.
                   (when-not (= next-state :__dead)
                     (recur (async/<! client-channel))))))
             (catch RuntimeException ex
               (log/error ex "Client comms thread just died")
               (raise [:client-comms-stopped
                       {:reason ex}]))
             (catch Exception ex
               (log/error ex "Client comms thread just died unexpectedly")
               (raise [:client-comms-failed
                       {:reason ex}]))
             (catch Throwable ex
               (log/error ex "Client comms thread just died a horrible death")
               (raise [:client-comms-error
                       {:reason ex}])))
           ;; Q: Doesn't hurt to close it twice, does it?
           (async/close! client-channel)

           (log/info "Communications loop exiting"))]
      (log/debug "Communications Thread set up")
      communications-thread)
    (catch RuntimeException ex
      (log/error ex "Failed to set up background comms")
      (raise [:background-comms
              {:reason ex}]))
    (catch Exception ex
      (log/error ex "Unexpected error setting up background comms")
      (raise [:background-comms/unexpected
              :reason ex]))
    (catch Throwable ex
      (log/error ex "*Really* unexpected error setting up background comms")
      (raise [:background-comms/thrown
              :reason ex]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn new-client-connection-thread
  []
  (map->ClientConnectionThread {}))
