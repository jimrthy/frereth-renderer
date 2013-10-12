(ns frereth-renderer.graphics
  (:require [clojure.core.async :as async]
            [clojure.pprint :refer (pprint)]
            [frereth-renderer.fsm :as fsm]
            [penumbra.app :as app]
            [penumbra.app.core :as core]
            [penumbra.opengl :as gl]
            [taoensso.timbre :as timbre
             :refer trace debug info warn error fatal spy with-log-level])
  (:gen-class))

;;; Information
;;; This probably doesn't actually belong here

(defn driver-version
  "What's available?
Note that penumbra has a get-version that returns a float version of the same value."
  []
  (gl/get-string :version))

;;; Initialization

(defn init-window
  "Initialize the static window where absolutely everything interesting will happen.
This approach is more than a little dumb...should really be able to create full-screen
windows that fill multiple monitors.
Baby steps. I'm just trying to get that rope thrown across the gorge."
  [{:keys [width height title] :as state}]
  (trace "Initializing window")
  (pprint state)
  ;; Have to pass in the window in question.
  ;; As annoying as it is, that's the way Protocols work.
  ;; Changing something that basic in Penumbra means very
  ;; breaking API changes. Probably doesn't matter, since I
  ;; seriously doubt anyone else is using this. But not
  ;; something that I have time for just now.
  (comment (app/vsync! nil true))

  ;; These don't seem to have penumbra equivalents (though that's probably just
  ;; because I haven't dug deep enough yet).
  ;; Whatever. I desperately need to do something along these lines.
  (comment (Display/setDisplayMode (DisplayMode. width height))
           (Display/setTitle title)
           (Display/create))
  state)

(defn init-gl
  "Set up the basic framework for rendering.
This is *definitely* not a long-term thing. Each world should be
specifying its own viewport. Most games will probably want multiple
modes for a HUD.
Baby steps."
  [{:keys [width height] :as state}]
  (gl/clear-color 0.5 0.0 0.5 0.0)
  
  state)

(defn init-fsm []
  (fsm/init {:disconnected
             {:client-connect-without-server
              [nil :waiting-for-server]
              :client-connect-with-server
              [nil :waiting-for-home-page]
              :client-connect-with-home-page
              [nil :main-life]}
             :waiting-for-server
             {:client-disconnect
              [nil :disconnected]}
             :server-connected
             {:waiting-for-home-page
              [nil :main-life]
              :client-disconnect
              [nil :disconnected]}
             :main-life
             {:client-disconnect
              [nil :disconnected]}}))

(defn init
  "Set up the 'main' window.
In the Stuart Sierra workflow-reloaded parlance, this is probably more of a start!"
  ([]
     ;; Surely there are some default parameters that make sense.
     ;; They should really come from whatever's doing the init, though.
     ;; Or maybe it should be a mixture of both. Whatever. Worry about
     ;; it later.
     (init {}))
  ([params]
      (let [renderer-state (init-window params)
            system-state (init-fsm)]
        {:renderer renderer-state
         :fsm system-state})))

(defn start [universe]
  (let [graphics (:graphics universe)
        renderer-state (:renderer graphics)
        windowing-state (init-gl renderer-state)
        fsm (fsm/start (:fsm graphics) :disconnected)]
    (into universe {:graphics {:renderer windowing-state
                               :fsm fsm}})))

(defn stop [universe]
  ;; FIXME: Is there anything I can do here?
  ;; (That's a pretty vital requirement)
  (into universe
        ;; Very tempting to close the window. Actually,
        ;; really must do that if I want to reclaim resources
        ;; so I can reset them.
        ;; That means expanding penumbra's API.
        ;; TODO: Make that happen.
        (fsm/stop (-> universe :graphics :fsm)))
  ;; FIXME: It would be much better to pass in the actual window(s)
  ;; that I want to destroy.
  ;; Then again, that may be totally pointless until/if lwjgl
  ;; gets around to actually switching to that sort of API.
  ;; This is the sort of quandary that makes me wish jogl were
  ;; less finicky about getting installed.
  ;; I don't think I want this.
  ;; TODO: The app has a :destroy! key that points to a function
  ;; that looks suspiciously as though it's what I actually want.
  (core/destroy! universe))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;; Drawing
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reshape
  "Should get called every time the window changes size.
For that matter, it should probably get called every time the window
changes position. In practice, it almost never seems to get called."
  [[x y w h] state]
  ;; FIXME: This fixed camera isn't appropriate at all.
  ;; It really needs to be set for whichever window is currently active.
  ;; But it's a start.
  ;; Besides...this is the vast majority of what init-gl was doing for starters.
  (gl/frustum-view 60.0 (/ (double w) h) 1.0 100.0)
  (gl/load-identity)
  state)

(defn draw-basic-triangles
  [{:keys [width height angle] :or {width 1 height 1 angle 0}}
   drawer]
  (println "Drawing a Basic Triangle")
  (pprint [width height angle drawer])
  (let [w2 (/ width 2.0)
        h2 (/ height 2.0)]
    (gl/translate w2 h2 0)
    (gl/rotate angle 0 0 1)
    (gl/scale 2 2 1)
    (gl/draw-triangles drawer)))

(defn draw-colored-vertex 
  [color vertex]
  (gl/color (color 0) (color 1) (color 2))
  (gl/vertex (vertex 0) (vertex 1)))

(defn draw-multi-colored-triangle
  [[color1 color2 color3] [vertex1 vertex2 vertex3]]
  (draw-colored-vertex color1 vertex1)
  (draw-colored-vertex color2 vertex2)
  (draw-colored-vertex color3 vertex3))

(defn draw-splash-triangle
  [params intensity]
  (letfn [(minimalist-triangle []
            (let [color1 [intensity 0.0 0.0]
                  color2 [0.0 intensity 0.0]
                  color3 [0.0 0.0 intensity]
                  vertex1 [100 0]
                  vertex2 [-50 86.6]
                  vertex3 [-50 -86.6]]
              (draw-multi-colored-triangle
               [color1 color2 color3]
               [vertex1 vertex2 vertex3])))]
    (draw-basic-triangles params minimalist-triangle)))

(defn draw-dead
  "Initializing...absolutely nothing interesting has happened yet"
  [params]
  ;; FIXME: Fill the screen with whitespace, or something vaguely
  ;; interesting
  (draw-splash-triangle params 0.25))

(defn draw-initial-splash
  "Rendering subsystem is up and ready to go. Waiting to hear from the client."
  [params]
  (draw-splash-triangle params 1.0))

(defn draw-secondary-splash
  "Have connected to the client. Waiting to hear back from the server"
  [params]
  (let [angle (:angle params)
        radians (Math/toRadians angle)
        cos (Math/cos radians)
        intensity (Math/abs cos)]
    (draw-splash-triangle params intensity)))

(defn draw-final-splash
  "Client's connected to the server. Just waiting for the handshake to
finish so we can start drawing whatever the server wants."
  [params]
  (draw-secondary-splash (into params
                               {:angle (* (:angle params) 2)})))

(defn update-initial-splash
  "Rotate the triangle periodically"
  [{:keys [width height angle last-time] :as params}]
  (let [cur-time (System/currentTimeMillis)
        delta-time (- cur-time last-time)
        next-angle (+ (* delta-time 0.05) angle)
        next-angle (if (>= next-angle 360.0)
                     (- next-angle 360.0)
                     next-angle)]
    (into params {:angle next-angle :last-time cur-time})))

(defn update
  [params]
  (let [actual-update (:update-function params)
        updated (actual-update params)
        drawer (:draw-function params)]
    (drawer updated)
    updated))

(defn fps->millis
  "Silly utilitiy function. At x frames per second, each individual
frame should be on the screen for y milliseconds.
FIXME: This should go away completely. Penumbra already has
utility functions that handle this better."
  [fps]
  (Math/round (float (* (/ 1 fps) 1000))))

;; FIXME: This next function is almost totally obsolete. Except that I desperately
;; need something to track the FSM.
;; IOW: keep the code around until I get it refactored into actually making that work.
(comment (defn run-splash
           "Show an initial splash screen while we're waiting for something interesting."
           [params]
           ;; Seriously. Switch to taking advantage of the FSM
           (throw (RuntimeException. "Obsolete method"))

           ;; Try to read from controller. Use an alt with a 60 FPS timeout.
           ;; This function shouldn't last long at all.
           (let [initial-time (System/currentTimeMillis)
                 frame-length  (fps->millis 60)
                 controller (:controller params)]
             (loop [params (into params {:update-function update-initial-splash
                                         :draw-function draw-initial-splash
                                         :last-time initial-time
                                         :angle 0.0})]
               ;; Just eliminate this next block for now.
               ;; I want the state manipulation pieces of it, but not the way
               ;; that's tied into drawing.
               (comment (when (not (Display/isCloseRequested))
                          (let [updated-params (update params)]
                            (Display/update)
                            ;; Q: Do I really want to create a new timeout each frame?
                            ;; A: Must. timeout involves an absolute value after the
                            ;; channel is created.
                            (let [next-frame (async/timeout frame-length)]
                              (let [[value channel] 
                                    (async/alts!! [controller next-frame])]
                                ;; If the channel actually closed, something bad has happened.
                                ;; TODO: is that true?

                                ;; I expect this to mostly timeout. What is value then?
                                (if (= channel next-frame)
                                  (do
                                    ;; Waiting
                                    (recur updated-params))
                                  (do
                                    ;; TODO: Be smarter about different values.
                                    ;; (i.e. Different messages mean different things to
                                    ;; the FSM).
                                    ;; Probably want an explicit state machine here.
                                    (if (= value :sterile-environment)
                                      (recur (into updated-params 
                                                   {:draw-function draw-secondary-splash}))
                                      (do
                                        ;; I am going to have to handle this...it's where
                                        ;; life starts to get interesting.
                                        (throw (RuntimeException. (str "Unhandled transition:"
                                                                       value))))))))))))))))

(defn draw-unknown-state [params]
  ;; FIXME: Do something better here.
  ;; Think Sad Mac or BSOD.
  (throw (RuntimeException. (str "Unknown State: " params))))

(defn draw-main [params]
  (throw (RuntimeException. "Draw whatever the client has told us to!")))

(defn display
  "Draw the next frame."
  [[delta time] state]
  ;; In a nutshell, I need an FSM:
  ;; 1) Show basic splash
  ;; 2) After we're talking to the client, switch to a second splash
  ;; 3) When the client starts telling us what to draw, switch to that.
  ;; Q: What does that actually look like?
  ;; A: Well, pretty definitely not like this.
  (let [state (:state @(:fsm state))
        drawer
        (condp = state
          :__dead draw-dead
          :disconnected draw-initial-splash
          :waiting-for-server draw-secondary-splash
          :server-connected draw-final-splash
          :main-life draw-main
          draw-unknown-state)]
    (trace "Current Situation: " state)
    (drawer state)

    ;; Signal to refresh and redraw the next frame.
    ;; Overall, I probably don't want to do this. There will be
    ;; screens that are basically static and don't need to be redrawn
    ;; very often.
    ;; That's an optimization for the future. For now, I need to get
    ;; something minimalist working.
    (app/repaint!)))

(defn begin-eye-candy-thread
  [visual-details]
  "Graphics first and foremost: the user needs eye candy ASAP."
  (app/start
   ;; TODO: Need the other callbacks to let the client know what's going on
   ;; (the Input side of the I/O)
   {:init init
    :display display
    :reshape reshape}
   visual-details))

(defn begin-communications
  " Actually updating things isn't as interesting [at first] or [quite]
as vital...but it's a very close second in both
categories.
OTOH, this really belongs elsewhere."
  [state]
  (let [control-channel (:controller state)
        fsm-atom (:fsm state)]
    (async/go
     (let [msg (async/<! control-channel)]
       ;; FIXME: Need a "quit" message.
       ;; This approach misses quite a few points, I think.
       ;; This pieces of the FSM should be for very coarsely-
       ;; grained transitions...
       ;; then again, maybe my entire picture of the architecture
       ;; is horribly flawed.
       ;; TODO: Where's the actual communication happening?
       ;; All I really care about right here, right now is
       ;; establishing the heartbeat connection.
       (fsm/transition! @fsm-atom msg true)))))

(defn begin
  "Kick off the threads where everything interesting happens."
  [visual-details]

  (begin-eye-candy-thread visual-details)
  (begin-communications (visual-details)))
