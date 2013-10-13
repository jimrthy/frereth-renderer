(ns frereth-renderer.system
  (:require ;[clojure.core.async :as async]
            [frereth-renderer.communications :as comm]
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
  {;; Actual socket layer for communicating with the client.
   :messaging (comm/init)

   ;; TODO: Create a "Top Level" window?
   ;; Note: if I decide to go with LWJGL, I should be able
   ;; to have multiple windows by using multiple classloaders
   ;; and loading the library separately into each.
   ;; Seems like overkill...but also an extremely good idea.
   ;; Note that the denizens of #clojure on IRC pretty
   ;; universally discourage the use of multiple class loaders
   ;; ...except for projects where they really and truly
   ;; make sense.

   :front-end (atom nil)
   :visualizer (atom nil)
   :graphics (graphics/init)})

(defn start
  "Perform the side-effects to bring a dead system to life"
  [universe]
  ;; FIXME: Debug only
  (timbre/set-level! :trace)

  (trace "START")

  ;; FIXME: Graphics first.
  ;; Besides. This doesn't really belong here.
  (let [messaging (comm/start (:messaging universe))
        visualization-channel (:local-mq messaging)

        ;; TODO: Don't use magic numbers.
        ;; TODO: Remember window positions from last run and reset them here.
        ;; N.B.: That really means a custom classloader. Which must happen
        ;; eventually anyway.
        ;; Q: Why? On both counts?
        ;; I just want to put it off as long as possible.
        visual-details  {:width 1024
                         :height 768
                         :title "Frereth"
                         :controller visualization-channel
                         :fsm (:fsm universe)}
        eye-candy (fn []
                    (graphics/begin visual-details))
        ;; TODO: Don't just use raw threads!
        ;; At the very least, use Java's built-in thread pool.
        ui-thread (Thread. eye-candy)]
    (.start ui-thread)
    ;; FIXME: This next name leaves a lot to be desired
    ;;(reset! (:visualizer universe) visualization-channel)
    (reset! (:front-end universe) ui-thread)
    (reset! (:messaging universe) messaging)

    universe))

(defn stop
  "Perform the side-effects to sterilize a universe"
  [universe]
  (trace "Telling the visualizer to exit")
  ;; I was originally getting a NPE here.
  ;; It's gone away.
  ;; Q: What's up with that?
  ;; And, really, what was I thinking here?
  (comment (async/>!! @(:visualizer universe) :exiting)
           (trace "Closing the control channel"))
  (comment (async/close! @(:control-channel universe)))
  (comment
    (trace "Closing the socket to the client")
    (when-let [client-socket @(:client-socket universe)]
      (mq/close client-socket))

    ;; Realistically: want to take some time to allow that socket to wrap
    ;; everything up.
    (println "Terminating the messaging context")
    (mq/terminate @(:messaging universe)))
  (comm/stop! (:messaging universe))

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
  (info "Resetting to a dead universe so the old can be garbage-collected")
  ;; FIXME: Calling init here seems like a horrid idea...except that it's
  ;; probably a decent choice.
  (init))

