(ns frereth-renderer.client-test
  (:require [clojure.core.async :as async]
            [clojure.pprint :refer [pprint]]
            [frereth-renderer.communications :as comms]
            [frereth-renderer.fsm :as fsm]
            [frereth-renderer.graphics :as g]
            [frereth-renderer.system :as sys])
  (:use midje.sweet))

(facts "Render listens for client messages and adjusts state accordingly."
       (println "Pretending to be a client that's connecting")
       ;; This is almost a test of the FSM, but I need *something*
       ;; to try to exercise some of the renderer code.
       ;; Honestly, these messages should be going to the 
       (let [initial (sys/init)
             alive (sys/start initial)]
         ;; It wasn't, but it is now.
         (comment (println "I don't think sys/start is ever actually returning"))
         (let [commands (-> alive :messaging deref :command)
               fsm-agent (:state alive)]
           (comment (println "Kicking off the test"))
           (fact "FSM starts out disconnected"
                 (println "Initial FSM:")
                 (pprint fsm-agent)
                 ;; This next test is failing, leaving me with a running app, which
                 ;; I desperately do not want.
                 (fsm/current-state fsm-agent) => :disconnected)
           (pprint @fsm-agent)
           (fact "Connecting without server from :disconnected -> :waiting-for-home-page"
                 (async/>!! commands :client-connect-without-server)
                 (fsm/current-state fsm-agent) => :waiting-for-home-page)
           (fact "Connected client that connects to server -> :main-life"
                 (async/>!! commands :server-connected)
                 (fsm/current-state fsm-agent) => :main-life)
           (fact "Most messages do nothing here"
                 ;; TODO: Randomly generate a bunch of symbols and feed them in as
                 ;; transitions
                 (async/>!! commands :fake-bogus)
                 (fsm/current-state fsm-agent) => :main-life)
           (fact "User input does not affect the FSM"
                 (g/key-press \a alive)
                 (fsm/current-state fsm-agent) => :main-life
                 (g/key-type \s alive)
                 (fsm/current-state fsm-agent) => :main-life
                 (g/key-release \a alive)
                 (fsm/current-state fsm-agent) => :main-life)
           (fact "Can disconnect the client"
                 (async/>!! commands :client-disconnect)
                 (fsm/current-state fsm-agent) => :disconnected)
           (fact "Killing the FSM should also kill the command thread"
                 (fsm/stop fsm-agent)
                 (fsm/current-state fsm-agent) => :__dead
                 ;; But I have to send one final message on the command channel
                 (async/>!! commands :anything))
           (fact "I need some way to verify that that thread's gone."
                 (let [timeout (async/timeout 500)
                       [final-response c] (async/alts! [commands timeout])]
                   (nil? final-response) => true
                   c => commands)
                 (let [terminator (-> alive :messaging deref :terminator)
                       timeout (async/timeout 500)
                       [v ch] (async/alts! [terminator timeout])]
                   ch => terminator
                   v => :game-over)))))

(facts "Can also kill everything by just closing the command channel"
       (let [initial (sys/init)
             alive (sys/start initial)
             commands (-> alive :messaging deref :command)
             fsm-agent (:state alive)]
         (fact "Can potentially go directly to main"
               (async/>!! commands :client-connect-with-home-page)
               (fsm/current-state fsm-agent) => :main-life)
         (fact "How can I verify that the thread went away?"
          (async/close! commands)
          (let [terminator (-> alive :messaging deref :terminator)
                timeout (async/timeout 500)
                [v ch] (async/alts! [terminator timeout])]
            ch => terminator
            v => :game-over))))