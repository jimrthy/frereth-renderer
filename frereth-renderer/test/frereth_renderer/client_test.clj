(ns frereth-renderer.client-test
  (:require [clojure.core.async :as async]
            [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            [frereth-renderer.communications :as comms]
            [frereth-renderer.config :as cfg]
            [frereth-renderer.events :as e]
            [frereth-renderer.fsm :as fsm]
            [frereth-renderer.graphics :as g]
            [frereth-renderer.system :as sys]
            [midje.sweet :refer (facts fact)]
            [ribol.core :refer (raise)]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

(facts "Render listens for client messages and adjusts state accordingly."
       (log/info "Pretending to be a client that's connecting")
       ;; This is almost a test of the FSM, but I need *something*
       ;; to try to exercise some of the renderer code.
       ;; Honestly, these messages should be going to the 
       (let [initial (sys/build {:fsm-description (cfg/default-fsm)})
             alive (component/start initial)]
         (try
           ;; TODO: These commands should be going over the external
           ;; socket instead.
           ;; Should probably make that a duplicate test, just for
           ;; the sake of completeness.
           (let [channels (:channels alive)
                 fsm (:fsm alive)]
             (if-let [commands (:cmd channels)]
               (if-let [fsm-agent (:manager fsm)]
                 (do
                   (fact "FSM starts out disconnected"
                         (log/info "render<->client FSM traversal\n"
                                   "Initial FSM:\n"
                                   (with-out-str (pprint fsm-agent)))
                         ;; This next test is failing, leaving me with a running app, which
                         ;; I desperately do not want.
                         (fsm/current-state fsm) => :disconnected)
                   (pprint @fsm-agent)
                   (fact "Connecting without server from :disconnected -> :waiting-for-home-page"
                         (async/>!! commands :client-connect-without-server)
                         (fsm/current-state fsm) => :waiting-for-home-page)
                   (fact "Connected client that connects to server -> :main-life"
                         (async/>!! commands :server-connected)
                         (fsm/current-state fsm) => :main-life)
                   (fact "Most messages do nothing here"
                         ;; TODO: Randomly generate a bunch of symbols and feed them in as
                         ;; transitions
                         (async/>!! commands :fake-bogus)
                         (fsm/current-state fsm) => :main-life)
                   (fact "User input does not affect the FSM"
                         (e/key-press \a alive)
                         (fsm/current-state fsm) => :main-life
                         (e/key-type \s alive)
                         (fsm/current-state fsm) => :main-life
                         (e/key-release \a alive)
                         (fsm/current-state fsm) => :main-life)
                   (fact "Can disconnect the client"
                         (async/>!! commands :client-disconnect)
                         (fsm/current-state fsm) => :disconnected)
                   (fact "Killing the FSM should also kill the command thread"
                         ;; This test smells bad
                         (component/stop fsm-agent)
                         (fsm/current-state fsm) => :__dead
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
                           v => :game-over)))
                 (raise [:init-fail {:problem "Missing FSM agent"
                                     :component alive}]))
               (do
                 (log/error "No command channel in\n"
                            (with-out-str (pprint channels)))
                 (raise [:init-fail {:problem "Missing command async channel"
                                     :component alive}]))))
           (finally
             (component/stop alive)))))

(facts "Can also kill everything by just closing the command channel"
       (let [initial (sys/init {})
             alive (component/start initial)
             commands (-> alive :messaging deref :command)
             fsm-agent (:state alive)]
         (try
           (fact "Can potentially go directly to main"
                 (async/>!! commands :client-connect-with-home-page)
                 (fsm/current-state fsm-agent) => :main-life)
           (fact "How can I verify that the thread went away?"
                 (async/close! commands)
                 (let [terminator (-> alive :messaging deref :terminator)
                       timeout (async/timeout 500)
                       [v ch] (async/alts! [terminator timeout])]
                   ch => terminator
                   v => :game-over))
           (finally
             (component/stop alive)))))
