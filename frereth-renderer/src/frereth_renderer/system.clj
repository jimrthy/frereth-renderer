(ns frereth-renderer.system
  (:require [cljeromq.core :as mq]
            [clojure.core.async :as async]
            [frereth-renderer.config :as config]
            [frereth-renderer.fsm :as fsm]
            [frereth-renderer.graphics :as graphics]
            [taoensso.timbre :as timbre
             :refer (trace debug info warn error fatal spy with-log-level)])
  (:gen-class))

(defn init
  "Generate a dead system"
  []
  (info "INIT")
  {:messaging (atom nil)
   :client-socket (atom nil)
   ;; TODO: Create a "Top Level" window?
   ;; Note: if I decide to go with LWJGL, I should be able
   ;; to have multiple windows by using multiple classloaders
   ;; and loading the library separately into each.
   ;; Seems like overkill...but also an extremely good idea.
   :control-channel (atom nil)
   :front-end (atom nil)
   :visualizer (atom nil)
   :graphics (graphics/init)})

(defn start
  "Perform the side-effects to bring a dead system to life"
  [universe]
  ;; FIXME: Debug only
  (timbre/set-level! :trace)

  (trace "START")
  (let [visualization-channel (async/chan)
        ;; TODO: Don't use magic numbers.
        ;; TODO: Remember window positions from last run and reset them here.
        ;; N.B.: That really means a custom classloader. Which must happen
        ;; eventually anyway. I just want to put it off as long as possible.
        visual-details  {:width 1024
                         :height 768
                         :title "Frereth"
                         :controller visualization-channel
                         :fsm (:fsm universe)}
        eye-candy (fn []
                    (graphics/begin visual-details))
        ;; TODO: Don't just use raw threads!
        ;; At the very least, use Java's built-in thread pool.
        visualization-thread (Thread. eye-candy)]
    (.start visualization-thread)
    (reset! (:visualizer universe) visualization-channel)
    (reset! (:front-end universe) visualization-thread)

    universe))

(defn stop
  "Perform the side-effects to sterilize a universe"
  [universe]
  (trace "Telling the visualizer to exit")
  ;; I was originally getting a NPE here.
  ;; It's gone away.
  ;; What's up with that?
  (async/>!! @(:visualizer universe) :exiting)
  (trace "Closing the control channel")
  (async/close! @(:control-channel universe))
  (trace "Closing the socket to the client")
  (mq/close @(:client-socket universe))

  ;; Realistically: want to take some time to allow that socket to wrap
  ;; everything up.
  (println "Terminating the messaging context")
  (mq/terminate @(:messaging universe))

  (println "Killing the agents")
  (shutdown-agents)

  (println "Terminating the UI")
  ;; Q: Would it be better to send a message over the controller channel?
  ;; Especially since I seem to be doing that already?
  ;; A: That seems like total overkill. There's no reason at all for this to
  ;; be either asynchronous or to involve anything like a callback.
  ;; Besides...that really tells it to switch to a "exiting" screen.
  ;; This is what actually kills it.
  (graphics/stop universe)

  ;; This step seems problematic...does it rebuild the agent pool so that we
  ;; can't exit after all?
  ;; More importantly: I'm not actually killing the app window yet
  ;; in graphics/stop universe. So, for now, old windows will have to be
  ;; closed manually. Which causes NPE's.
  ;; FIXME: Fix that NPE on window close
  ;; FIXME: Destroy the previous app window. Or recycle it.
  ;; (Destroying better, from a "start over with a clean slate"
  ;; perspective)
  (println "Resetting to a dead universe so the old can be garbage-collected")
  (init))

