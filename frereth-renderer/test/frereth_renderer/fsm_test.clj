(ns frereth-renderer.fsm-test
  (:require [frereth-renderer.fsm :as fsm]
            [clojure.pprint :refer [pprint]])
  (:use midje.sweet))

(facts "From a bingo game:"
       ;; This gets extremely repetitive.
       ;; Should really be able to specify transitions that apply
       ;; to all (or even most) states.
       (let [dead (fsm/init {:initialized
                             {:log-in [nil :logged-in]}
                             :logged-in
                             {:add-money [nil :ready-to-play]
                              :log-out [nil :initialized]}
                             ;; Here's a major flaw:
                             ;; transitioning into ready-to-play really
                             ;; depends on having money available.
                             ;; So, I really do need functions attached
                             ;; to entering/leaving a state, as well
                             ;; as functions associated with specific
                             ;; transitions.
                             ;; TODO: Add that.
                             :ready-to-play
                             {:start-play [nil :ball-draw]
                              :log-out [nil :initialized]}
                             :ball-draw
                             {:log-out [nil :initialized]
                              :cancel [nil :ready-to-play]
                              :last-ball-received [nil :animating]}
                             :animating
                             {:log-out [nil :initialized]
                              :cancel [nil :ready-to-play]
                              :complete [nil :awaiting-daub]}
                             :awaiting-daub
                             {:log-out [nil :initialized]
                              :cancel [nil :ready-to-play]
                              :daub-prize [nil :prize-animation]
                              :daub-no-prize [nil :ready-to-play]}
                             :prize-animation
                             {:log-out [nil :initialized]
                              :cancel [nil :ready-to-play]
                              :complete [nil :ready-to-play]}})
             m (fsm/start dead :initialized)]
         (fact "initialized"
               (Thread/sleep 100)
               (fsm/current-state m) => :initialized)

         (fact "log in"
               (fsm/transition! m :log-in)
               (Thread/sleep 100)
               (fsm/current-state m) => :logged-in)
         
         (fact "add money"
               (fsm/transition! m :add-money)
               (Thread/sleep 100)
               (fsm/current-state m) => :ready-to-play)
         
         (fact "start"
               (fsm/transition! m :start-play)
               (Thread/sleep 100)
               (fsm/current-state m) => :ball-draw)

         (fact "finish ball draw"
               (fsm/transition! m :last-ball-received)
               (Thread/sleep 100)
               (fsm/current-state m) => :animating)

         (fact "finish animation"
               (fsm/transition! m :complete)
               (Thread/sleep 100)
               (fsm/current-state m) => :awaiting-daub)

         (fact "Daub, with prize"
               (fsm/transition! m :daub-prize)
               (Thread/sleep 100)
               (fsm/current-state m) => :prize-animation)

         (fact "Prize animation complete"
               (fsm/transition! m :complete)
               (Thread/sleep 100)
               (fsm/current-state m) => :ready-to-play)

         (fact "Cash Out"
               (fsm/transition! m :log-out)
               (Thread/sleep 100)
               (fsm/current-state m) => :initialized)

         (fact "Illegal Transition"
               (fsm/transition! m :illegal true)
               (Thread/sleep 100)
               (let [s (fsm/current-state m)]
                 (comment
                   (do
                     (println "Result of current-state:\n'")
                     (pprint s)
                     (println "'\nAnd that's all I have to say about that")))
                 
                 (:object s) => 
                 (contains {:failure :transition :which :illegal})))

         (fact "Clear Error"
               (fsm/clear-error m)
               (fsm/current-state m) => :initialized)))
