(ns frereth-renderer.fsm
  (:require [clojure.core.async :as async]
            [clojure.core.contracts :as contract]
            [com.stuartsierra.component :as component]
            [frereth-renderer.util :as util]
            [ribol.core :refer (manage raise)]
            [schema.core :as s]
            [schema.macros :as sm]
            [taoensso.timbre :as log])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;; N.B. side-effect really needs to be a function that takes
;; no arguments, does not perform any i/o, and will not block.
;; TODO: Add something like a serious-side-effect key for that
;; sort of thing. If it's present, can handle it inside send-off
;; instead.
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
   (log/debug "Starting the FSM. Manually adjusting the Manager from\n"
              (util/pretty @manager) "\nto\n" initial-state)
   ;; This is getting a little annoying in my debugging. I'm more
   ;; than a little tempted to turn this into a class where I get to
   ;; control its printed output. That seems like a really stupid
   ;; reason to do something so drastic.
   (set-error-mode! manager :fail)
   (assoc this :manager 
          (send manager (fn [initial]
                          (merge initial initial-description
                                 {:__state initial-state})))))

  (stop
   [this]
   (send manager (fn [initial]
                   (assoc initial :__state :__done)))
   ;; If I do this, there aren't really any options to restart.
   (comment (shutdown-agents))
   this))

(sm/defn transitions->state-pairs :- [(s/one [(s/one s/Keyword)
                                              (s/one State)])]
  [states :- TransitionMap]
  (let [pairs (map (fn [[k v]]
                     [k (strict-map->State {:edges v})])
                   states)]))

(sm/defn state-pairs->description :- Description
  [initial-pairs :- [(s/one [(s/one s/Keyword) (s/one State)])]
   hard-coded :- TransitionMap]
  (let [initial-map (reduce (fn [acc [k v]]
                              (assoc acc k v))
                            initial-pairs)]
    (s/validate Description
                (into initial-map
                      hard-coded))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(sm/defn init :- FiniteStateMachine
  "Returns a dead FSM.
Note that this is *not* intended to really be a Component.
It's more of a preliminary thing that Start will turn into
the FSM component.

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
  [states :- TransitionMap
   initial-state :- s/Keyword]
  (let [hard-coded {:__done {:edges {:pre-init {:next-state :__pre-init}}}
                    :__pre-init {:edges {:init {:next-state :__init}
                                         :cancel {:next-state :__dead}}}
                    :__init nil}
        mgr (agent {:__state :__pre-init}
                   :error-mode :continue
                   :error-handler (fn [the-agent ex]
                                    (log/error ex "Agent should transition to an error state")
                                    ;; Q: What makes sense here?
                                    (set-error-mode! the-agent :fail)))
        base-line {:initial-description hard-coded
                   :manager mgr
                   :initial-state initial-state}]
    (if states
      ;; TODO: Refactor this into its own method so I can unit test it.
      (let [initial-pairs (transitions->state-pairs states)]
        (log/debug "Went from\n" states
                   "\nto\n" initial-pairs
                   "\nTrying to convert to a Description:")
        (let [descr (state-pairs->description initial-pairs hard-coded)]
          (strict-map->FiniteStateMachine
           (assoc-in base-line [:description] descr))))
      (do (log/debug "Creating an empty FSM. This will probably backfire")
          (map->FiniteStateMachine base-line)))))

(defn transition-agent [prev transition-key error-if-unknown]
  ;; Wow. This just seems incredibly ugly.
  ;; Aside from not actually working.
  (log/info "Trying to adjust " prev " by " transition-key)
  (if-let [{:keys [state]} prev]
    (if-let [transitions (state prev)]
      (if-let [transition (transitions transition-key)]
        (if-let [{:keys [side-effect next-state]} transition]
          (do
            (when side-effect
              ;; N.B. side-effect should not perform i/o or block
              ;; TODO: Should probably just automatically do any
              ;; side-effects inside send-off instead, just to be safe.
              (side-effect))
            ;; Success!
            (comment) (log/trace "Switching from " (:state prev) " to " next-state)
            (into prev {:state next-state})) ; Yes. This is the interesting part
          (if error-if-unknown
            (do
              (comment (log/trace "Set to error out on illegal transition"))
              ;; Note that this should put the agent into an error state.
              (raise (into prev {:failure :transition :which transition-key})))
            (log/debug "Ignoring illegal transition"))))
      (do
        (log/error "No transitions from " prev " which is in State " state)
        ;; FSM doesn't know anything about its current state.
        ;; Not really any way around this either
        (raise (into prev {:failure :state :which state}))))
    (do
      (log/error "No existing state in " prev)
      ;; FSM is totally missing its current state.
      ;; This is a fairly serious error under any circumstances.
      (raise (into prev {:failure :missing :which :state})))))

(defn send-transition
  "Sends a transition request.
error-if-unknown allows caller to ignore requests to perform
an illegal transition for the current state.
That part is extremely easy to abuse and should almost definitely
go away.
OTOH...it can be extremely convenient"
  [fsm transition-key & error-if-unknown]
  (log/debug "FSM Transition!\nSending " transition-key " to " (util/pretty fsm))
  (manage
   (if-let [mgr (:manager fsm)]
     (if-let [failure (agent-error mgr)]
       ;; TODO: Add error recovery
       (log/error failure "FSM in an error state. Fix that and try again")
       (send mgr transition-agent transition-key error-if-unknown))
     (do
       (log/error "Missing Agent that should be managing the FSM in:\n"
                  (util/pretty fsm))
       (raise [:broken-fsm
               {:value fsm
                :members (keys fsm)}])))
   (catch ClassCastException ex
     (log/error "Broken FSM transition: trying to send '"
                (str transition-key) "' to\n'" (str fsm) "'")
     ;; This definitely falls in the category of "fail early"
     (raise [:bad-fsm-transition {:reason ex}]))))

(defn current-state [fsm]
  ;; This seems more than a little silly.
  ;; But it fits in with keeping this a block box that allows me
  ;; to swap implementation in and out.
  ;; Just because clojure doesn't do things like data hiding
  ;; doesn't mean I should avoid it when it makes sense.
  (if-let [fsm-agent (:manager fsm)]
    (if-let [^RuntimeException ex (agent-error fsm-agent)]
      (do
        (log/warn "Agent is in an error state\n"
                  (.getMessage ex)
                  (util/pretty (.getStackTrace ex))
                  "\n"
                  (util/pretty (.getCause ex))
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

