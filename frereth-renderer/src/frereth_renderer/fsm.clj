(ns frereth-renderer.fsm
  (:require [clojure.core.async :as async]
            [clojure.core.contracts :as contract]
            ;;[slingshot.slingshot :refer [throw+ try+]]
            [taoensso.timbre :as timbre])
  (:use ribol.core)
  (:gen-class))

;;; It's very tempting to turn this entire thing into a
;;; closure. That doesn't seem to gain me anything now,
;;; but...it's tempting.
;;; Really need to make that call before this gets used
;;; heavily.

(defn init
  "Returns a dead FSM.
The description should be a map that looks like:
 {:state1
   {:transition1
    [side-effect-fn
     modified-state]
    :transition2
    [side-effect-fn
     modified-state]}}
Reserved state keys:
Keywords that start with __
Actually used:
:__dead

nil-side-effect-fn just mean make the transition
without side-effects.
This can easily be abused, but it also provides
an extremely rich API for cheap.
TODO: Would core.async be more appropriate than agents?"
  [description]
  ;; The simplest thing that could possibly work
  (let [a (agent (into description {:state :__dead}))]
    (set-error-mode! a :fail)
    a))

(defn transition!
  "Sends a transition request.
error-if-unknown allows caller to ignore requests to perform
an illegal transition for the current state.
That part is extremely easy to abuse and should almost definitely
go away.
OTOH...it can be extremely convenient"
  [fsm transition-key & error-if-unknown]
  (timbre/trace "FSM Transition!\nSending " transition-key " to " fsm)
  (manage
    (send fsm (fn [prev]
                ;; Wow. This just seems incredibly ugly.
                ;; Aside from not actually working.
                (if-let [{:keys [state] :as machine} prev]
                  (if-let [transitions (state prev)]
                    (if-let [[side-effect updated] (transitions transition-key)]
                      (do
                        (when side-effect
                          (side-effect))
                        ;; Success!
                        (comment (timbre/trace "Switching from " (:state prev) " to " updated))
                        (into prev {:state updated}))
                      (if error-if-unknown
                        (do
                          (comment (timbre/trace "Set to error out on illegal transition"))
                          ;; Note that this should put the agent into an error state.
                          (raise (into prev {:failure :transition :which transition-key})))
                        (comment (timbre/trace "Ignoring illegal transition"))))
                    ;; FSM doesn't know anything about its current state.
                    ;; Not really any way around this either
                    (raise (into prev {:failure :state :which state})))
                  ;; FSM is totally missing its current state.
                  ;; This is a fairly serious error under any circumstances.
                  (raise (into prev {:failure :missing :which :state})))))
    (catch ClassCastException ex
      (timbre/error "Broken FSM transition: trying to send '"
             (str transition-key) "' to\n'" (str fsm) "'")
      ;; This definitely falls in the category of "fail early"
      (throw))))

(def start!
  (contract/with-constraints
    (fn [fsm initial-state]
      ;; Special case. Can't actually use transition! because it doesn't
      ;; know how to bring a dead FSM to life.
      (timbre/info "Starting FSM, state: " initial-state)
      (send fsm (fn [current]
                  (into current {:state initial-state}))))

    (contract/contract dont-revive-living
              "Brings a dead FSM to life"
              [fsm initial-state]
              [(= (:state @fsm) :__dead)
               =>
               #(= initial-state %)])))

(defn current-state [fsm-agent]
  ;; This seems more than a little silly.
  ;; But it fits in with keeping this a block box that allows me
  ;; to swap implementation in and out.
  ;; Just because clojure doesn't do things like data hiding
  ;; doesn't mean I should avoid it when it makes sense.
  (if-let [ex (agent-error fsm-agent)]
    (do
      (comment) (timbre/trace "Agent is in an error state")
      (.data ex))
    (if-let [m @fsm-agent]
      (do
        (comment (timbre/trace "Agent state is just fine, thank you"))
        (:state m))
      (do
        (timbre/error "NULL FSM agent. WTH?")
        (raise {:error :broken-fsm
                :agent fsm-agent})))))

(defn clear-error [fsm]
  (let [state @fsm]
    (restart-agent fsm (dissoc state :failure :which))))

(defn stop
  "Kill an FSM"
  [fsm]
  (send fsm (fn [_]
              {:state :__dead}))
  ;; FIXME: Do I want to also run:
  (comment (shutdown-agents))
  ;; ? I'm not seeing any pros/cons online.
  ;; TODO: check on IRC
  fsm)
