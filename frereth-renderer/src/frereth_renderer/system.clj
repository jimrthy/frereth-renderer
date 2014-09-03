(ns frereth-renderer.system
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [frereth-client.system :as client]
            [frereth-renderer.communications :as comm]
            [frereth-renderer.config :as config]
            [frereth-renderer.fsm :as fsm]
            [frereth-renderer.graphics :as graphics]
            [frereth-renderer.logging :as logging]
            [taoensso.timbre :as timbre
             :refer (trace debug info warn error fatal spy with-log-level)])
  (:gen-class))

(defn init
  "Generate a dead system"
  [overriding-config-options]
  (set! *warn-on-reflection* true)

  (let [cfg (into (config/defaults) overriding-config-options)]
    (-> (component/system-map
         :channels (comm/new-channels)
         :client (client/init)
         :client-socket (comm/new-client-socket)
         :client-url (comm/new-client-url cfg)
         :context (comm/new-context)
         :graphics (graphics/init)
         :logging (logging/new)
         :visualizer (graphics/new-visualizer))
        
        (component/system-using
         {:channels [:logging]
          :client-socket [:client-url :context]
          :client-url [:logging]
          :context [:logging]
          :graphics [:logging]}))))

(comment (defn quit-being-hermit
           "Make contact with the outside world"
           [universe]
           (let [ctx (mq/context 1)
                 socket (mq/connected-socket ctx :req (config/client-url))
                 chan (async/chan)]
             (reset! (:messaging universe) ctx)
             (reset! (:client-socket universe) socket)
             (reset! (:control-channel universe) chan)

             ;; Q: What should this do?
             ;; I mean, this is the basic gist. I want to start pinging the client.
             ;; When the client responds, notify the video channel.
             ;; Eventually, though, it should time out: waiting around forever
             ;; to get called back is for losers.
             ;; The first idea that comes to mind is something like a command
             ;; channel for letting this know about such a tragic fate.
             ;; The control-channel I just created seems like it might
             ;; be the perfect option for that sort of thing. Duh.
             (async/go 
              ;; Seems annoying that I can't just dispatch on an
              ;; an arbitrary keyword.
              (mq/send socket ":ready-to-draw")
              ;; FIXME: This needs to be equipped to deal with a timeout
              ;; (i.e. use a poll instead)
              (let [response (mq/recv socket)]
                (async/>! chan response))))))

(comment (defn start
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

           ;; Start by verifying that the existing pieces are dead.
           ;; Should probably do this even before the splash screen.
           ;; It isn't like it'll take long.
           ;; Then again: it'd be better to display any errors that happen there.
           (letfn [(verify-dead [key]
                     (timbre/trace "Verifying dead: " key)
                     (assert (nil? @(key universe))))]
             (doseq [k [:visualizer]]
               (verify-dead k)))

           (let [messaging (comm/start (:messaging universe))]

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
             (timbre/info "Graphics started")
             ;; After the splash screen is created, start dealing with some meat.

             ;; Shift the splash screen FSM, so that I have a hint that
             ;; processing is continuing.

             (comment
               ;; I bet that this call's the one that isn't returning
               (async/>!! visualization-channel :sterile-environment)
               (timbre/info "Nope. I got here")
               (assert visualization-channel)
               (reset! (:visualizer universe) visualization-channel))
             ;; Try doing it this way instead
             (let [command (-> :messaging universe deref :command)]
               (async/>!! command :sterile-environment)
               (timbre/info "FSM should be showing sterile now"))
             
             ;; FIXME: Was this something clever I added recently, or something that
             ;; went away when I moved the networking pieces, and that move
             ;; didn't get propagated?
             (comment (quit-being-hermit universe))

             ;; This is wrong...at this point, really should start feeding
             ;; messages back and forth between the channel and the 
             ;; client-socket.
             ;; Actually, no...this needs to wait until we get a response
             ;; from the client.
             (let [control (:command messaging)
                   ui (:user-input messaging)]
               (async/go
                 (let [timeout (async/timeout 250)]
                   (loop [[msg ch] (async/alts! [timeout control ui])]
                     (when msg
                       (timbre/trace "Forwarding message between client and UI -- " msg)
                       (when-let [dst (condp = ch
                                        control ui
                                        ui control)]
                         (async/>! dst msg))
                       (let [timeout (async/timeout 250)]
                         (recur (async/alts! [control ui timeout]))))
                     ;; TODO: Really ought to update *something* to show that this is going away.
                     (timbre/trace "client <-> renderer thread exiting")))))
             universe)))

(comment (defn stop
           "Perform the side-effects to sterilize a universe"
           [universe]

           (trace "Telling the visualizer to exit")

           (comm/stop! (:messaging universe))

           ;; FIXME: Does this next approach make any sense here?
           (comment
             ;; This next line fails miserably if the visualizer has already died.
             ;; (e.g. from an unhandled exception).
             ;; TODO: Make sure the visualizer doesn't do that.
             ;; TODO: Add a timeout and send using alts instead.
             ;; If the channel itself has died, this will just fail silently.
             (async/>!! @(:visualizer universe) :exiting)
             (async/close! @(:control-channel universe))
             (mq/close @(:client-socket universe)))

           ;; Realistically: want to take some time to allow that socket to wrap
           ;; everything up.
           (comment (mq/terminate @(:messaging universe)))
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
           (init)))
