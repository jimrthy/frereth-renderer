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

   ;;:front-end (atom nil)
   :visualizer (atom nil)
   :graphics (atom (graphics/init))})

(defn start
  "Perform the side-effects to bring a dead system to life"
  [universe]
  ;; FIXME: Debug only
  (timbre/set-level! :trace)

  (trace "START")

  ;; Pulled the next block out of the let just below.
  ;; Originally, I thought I wanted to call the graphics
  ;; kick-off in its own thread. Now I'm fairly certain
  ;; that I do not.
  "        eye-candy (fn []
                    (graphics/begin visual-details))
        ;; TODO: Don't just use raw threads!
        ;; At the very least, use Java's built-in thread pool.
        ui-thread (Thread. eye-candy)
;; And then in the let body:
    (.start ui-thread)
"

  (let [messaging (comm/start (:messaging universe))
        ;;visualization-channel (:local-mq messaging)
        ]

    ;; I really want to kick off graphics immediately.
    ;; Unfortunately for that plan, it needs access to
    ;; messaging.
    ;; Fortunately for my sorrow, that really doesn't
    ;; add much time at all to the startup sequence.
    ;; TODO: Split this up. I need an async channel to
    ;; communicate with graphics. I can pass it the
    ;; networking socket later.
    (reset! (:messaging universe) messaging)

    (swap! (:graphics universe) (fn [renderer]
                                  (graphics/start renderer
                                                  (:messaging universe))))

    universe))

(defn stop
  "Perform the side-effects to sterilize a universe"
  [universe]
  (trace "Telling the visualizer to exit")
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

