(ns frereth-renderer.fsm
  (:require [clojure.core.async :as async])
  (:gen-class))

(defn init
  "Returns a dead FSM"
  []
  ;; The simplest thing that could possibly work
  (agent {:state :dead}))

(defn start!
  "Brings a dead FSM to life"
  [fsm]
  (println "Trying to bring " fsm " to life")
  (send fsm (fn [current]
              ;; FIXME: Use clojure.contract instead!
              {:pre [(= (:state current) :dead)]}
              {:state :new-born}))
  nil)

(defn stop!
  "Kill an FSM"
  [fsm]
  (send fsm (fn [_]
              {:state :dead})))
