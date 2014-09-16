(ns frereth-renderer.fsm
  (:require [clojure.core.async :as async]
            [clojure.core.contracts :as contract]
            [clojure.pprint :refer (pprint)]
            [com.stuartsierra.component :as component]
            [ribol.core :refer (manage raise)]
            [schema.core :as s]
            [schema.macros :as sm]
            [taoensso.timbre :as log])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def Transition {(s/optional-key :side-effect) s/Any
                 :next-state s/Keyword})

(def TransitionMap {s/Keyword Transition})

(sm/defrecord State [edges :- TransitionMap])

(def Description {s/Keyword State})

(sm/defrecord FiniteStateMachine [initial-description :- Description
                                  manager :- clojure.lang.Agent
                                  initial-state :- s/Keyword]
  component/Lifecycle
  (start
   [this]
   ;; This is getting a little annoying in my debugging. I'm more
   ;; than a little tempted to turn this into a class where I get to
   ;; control its printed output. That seems like a really stupid
   ;; reason to do something so drastic.
   (set-error-mode! manager :fail)
   (send manager (fn [initial]
                   (merge initial initial-description
                          {:__state initial-state}))))

  (stop
   [this]
   (send manager (fn [initial]
                   (assoc initial :__state :__done)))
   ;; If I do this, there aren't really any options to restart.
   (comment (shutdown-agents))
   this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(sm/defn init :- FiniteStateMachine
  "Returns a dead FSM.
The description should be a map that looks like:
 {:state1
   {:transition1
    {side-effect-fn
     modified-state}
    :transition2
    {side-effect-fn
     modified-state}}}
Reserved state keys:
Keywords that start with __
Actually used:
:__done
:__pre-init
:__init
:__state

nil/missing side-effect-fn just mean make the transition
without side-effects.
This can easily be abused, but it also provides
an extremely rich API for cheap.
TODO: Would core.async be more appropriate than agents?"
  [states :- {s/Keyword Transition}
   initial-state :- s/Keyword]
  (let [hard-coded {:__done {:edges {:pre-init {:next-state :__pre-init}}}
                    :__pre-init {:edges {:init {:next-state :__init}
                                         :cancel {:next-state :__dead}}}
                    :__init nil}
        mgr (agent {:__state :__pre-init})
        base-line {:description hard-coded
                   :manager mgr}]
    (if states
      (let [initial-pairs (map (fn [[k v]]
                                 [k (strict-map->State {:edges v})])
                               states)]
        (log/debug "Went from\n" states
                   "\nto\n" initial-pairs
                   "\nTrying to convert to a Description:")
        (let [initial-map (hash-map initial-pairs)
              descr (s/validate Description
                                (into initial-map
                                      hard-coded))]
          (strict-map->FiniteStateMachine
           (assoc base-line :initial-state initial-state))))
      (do (log/debug "Creating the FSM with no initial states. This will probably backfire")
          (map->FiniteStateMachine base-line)))))

(defn send-transition
  "Sends a transition request.
error-if-unknown allows caller to ignore requests to perform
an illegal transition for the current state.
That part is extremely easy to abuse and should almost definitely
go away.
OTOH...it can be extremely convenient"
  [fsm transition-key & error-if-unknown]
  (log/trace "FSM Transition!\nSending " transition-key " to " (with-out-str (pprint fsm)))
  (manage
    (send fsm (fn [prev]
                ;; Wow. This just seems incredibly ugly.
                ;; Aside from not actually working.
                (if-let [{:keys [state] :as machine} prev]
                  (if-let [transitions (state prev)]
                    (if-let [transition (transitions transition-key)]
                      (if-let [{:keys [side-effect next-state]} transition]
                        (do
                          (when side-effect
                            (side-effect))
                          ;; Success!
                          (comment (log/trace "Switching from " (:state prev) " to " next-state))
                          (into prev {:state next-state}))  ; Yes. This is the interesting part
                        (if error-if-unknown
                          (do
                            (comment (log/trace "Set to error out on illegal transition"))
                            ;; Note that this should put the agent into an error state.
                            (raise (into prev {:failure :transition :which transition-key})))
                          (comment (log/trace "Ignoring illegal transition")))))
                    ;; FSM doesn't know anything about its current state.
                    ;; Not really any way around this either
                    (raise (into prev {:failure :state :which state})))
                  ;; FSM is totally missing its current state.
                  ;; This is a fairly serious error under any circumstances.
                  (raise (into prev {:failure :missing :which :state})))))
    (catch ClassCastException ex
      (log/error "Broken FSM transition: trying to send '"
             (str transition-key) "' to\n'" (str fsm) "'")
      ;; This definitely falls in the category of "fail early"
      (raise [:bad-fsm-transition {:reason ex}]))))

(defn current-state [fsm-agent]
  ;; This seems more than a little silly.
  ;; But it fits in with keeping this a block box that allows me
  ;; to swap implementation in and out.
  ;; Just because clojure doesn't do things like data hiding
  ;; doesn't mean I should avoid it when it makes sense.
  (if fsm-agent
    (if-let [^RuntimeException ex (agent-error fsm-agent)]
      (do
        (log/warn "Agent is in an error state\n"
                  (.getMessage ex)
                  (with-out-str (pprint (.getStackTrace ex))
                    (pprint (.getCause ex)))
                  "\n" (.toString ex)))
      (if-let [m @fsm-agent]
        (do
          (comment (log/trace "Agent state is just fine, thank you"))
          (:__state m))
        (do
          (log/error "NULL FSM agent. WTH?")
          (raise {:error :broken-fsm
                  :agent fsm-agent}))))
    (raise {:error :missing-fsm})))

(defn clear-error [fsm]
  (let [state @fsm]
    (restart-agent fsm (dissoc state :failure :which))))

