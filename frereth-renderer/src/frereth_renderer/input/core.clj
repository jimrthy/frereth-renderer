(ns frereth-renderer.input.core
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [frereth-renderer.fsm :as fsm]
            [ribol.core :refer (raise)]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(declare begin-communications)
(defrecord CommunicationsThread
    [communications visualizer stopper]
  component/Lifecycle
  (start [this]
    (log/trace "Starting communications thread")
    (let [stopper (async/chan)])
    (into this {:communications
                (begin-communications visualizer stopper)
                :stopper stopper}))
  (stop [this]
    (async/close! stopper)
    (into this {:communications nil
                :stopper nil})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(defn begin-communications
  " Actually updating things isn't as interesting [at first] or [quite]
as vital as the eye candy...but it's a very close second in both
categories.
OTOH, this really belongs elsewhere."
  [state stopper]
  (if-let [messaging-atom (:messaging state)]
    (let [messaging @messaging-atom
          control-channel (:command messaging)
          fsm-atom (:fsm state)]
      (log/trace "\n****************************************************
Initializing Communications
State: " state "\nMessaging: " (:messaging state)
"\nControl Channel: " control-channel "\nFSM Atom: " fsm-atom
"\n****************************************************")
      (let [communications-thread
            (async/go
              (loop [[c msg] (async/alts! control-channel stopper)]
                ;; Check for channel closed
                (when-not (nil? msg)
                  (log/trace "Control Message:\n" msg)

                  ;; FIXME: Need a "quit" message.
                  ;; This approach misses quite a few points, I think.
                  ;; This pieces of the FSM should be for very coarsely-
                  ;; grained transitions...
                  ;; then again, maybe my entire picture of the architecture
                  ;; is horribly flawed.
                  ;; TODO: Where's the actual communication happening?
                  ;; All I really care about right here, right now is
                  ;; establishing the heartbeat connection.

                  (log/info "Communications Loop Received\n" 
                            msg "\nfrom control channel")
                  (raise :start-here)

                  ;; This really isn't good enough. This also has to handle responses
                  ;; to UI requests. I'm torn between using yet another channel
                  ;; (to where?) for this and using penumbra's message queue.
                  ;; Then again, maybe requiring client apps to work with the
                  ;; FSM makes sense...except that now we're really talking about
                  ;; a multiple secondary FSMs which I have absolutely no control
                  ;; over. That doesn't exactly seem like a good recipe for a
                  ;; "happy path"       
                  (let [next-state (fsm/send-transition @fsm-atom msg true)]
                    ;; TODO: I don't think this is really even all that close
                    ;; to what I want.
                    (when-not (= next-state :__dead)
                      (recur (async/<! control-channel))))))
              ;; Q: Doesn't hurt to close it twice, does it?
              (async/close! control-channel)

              (log/info "Communications loop exiting")
              ;; TODO: Kill the window!!
              (let [terminal-channel (-> state :messaging deref :terminator)]
                ;; Realistically, I want this to be synchronous.
                ;; Can that happen inside a go block?
                ;; Oh well. It shouldn't matter all that much.
                (async/>! terminal-channel :game-over)))]
        (log/trace "Communications Thread set up")
        communications-thread))
    (raise [:broken
            {:missing :messaging-atom
             :state state}])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn new-communication-thread
  []
  (map->CommunicationsThread {}))

