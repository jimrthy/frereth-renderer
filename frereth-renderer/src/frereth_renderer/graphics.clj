(ns frereth-renderer.graphics
  (:require [clojure.core.async :as async]
            [frereth-renderer.fsm :as fsm]
            [penumbra.app :as app]
            [penumbra.opengl :as gl])
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
  (app/vsync! true)

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

(defn init
  "Set up the 'main' window.
In the Stuart Sierra workflow-reloaded parlance, this is probably more of a start!"
  [params]
  (fsm/start! (:fsm params))
  (let [state (init-window params)]
    (init-gl state)))

(defn stop! [universe]
  ;; FIXME: Is there anything I can do here?
  ;; (That's a pretty vital requirement)
  (comment (Display/destroy))
  (fsm/stop! (:fsm universe)))

;;; Drawing

(defn reshape [[x y w h] state]
  ;; FIXME: This fixed camera isn't appropriate at all.
  ;; It really needs to be set for whichever window is currently active.
  ;; But it's a start.
  ;; Besides...this is the vast majority of what init-gl was doing for starters.
  (gl/frustum-view 60.0 (/ (double w) h) 1.0 100.0)
  (gl/load-identity)
  state)

(defn draw-basic-triangles
  [{:keys [width height angle]}
   drawer]
  (let [w2 (/ width 2.0)
        h2 (/ height 2.0)]
    ;; More stuff that penumbra seems to have made obsolete.
    ;; Kind of desperately need to learn it.
    (comment (gl/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
             (gl/glLoadIdentity))
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

(defn draw-initial-splash
  [params]
  (draw-splash-triangle params 1.0))

(defn draw-secondary-splash
  [params]
  (let [angle (:angle params)
        radians (Math/toRadians angle)
        cos (Math/cos radians)
        intensity (Math/abs cos)]
    (draw-splash-triangle params intensity)))

(defn update-initial-splash
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
frame should be on the screen for y milliseconds."
  [fps]
  (Math/round (float (* (/ 1 fps) 1000))))

;; FIXME: This next function is almost totally obsolete. Except that I desperately
;; need something to track the FSM.
(defn run-splash
  "Show an initial splash screen while we're waiting for something interesting."
  [params]
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
                                                              value)))))))))))))))

(defn draw-unknown-state [params]
  (throw (RuntimeException. "Not Implemented")))

(defn display
  "As near as I can tell, this draws the next frame.
Which pretty much turns my original approach right on its ear."
  [[delta time] state]
  ;; In a nutshell, I need an FSM:
  ;; 1) Show basic splash
  ;; 2) After we're talking to the client, switch to a second splash
  ;; 3) When the client starts telling us what to draw, switch to that.
  ;; Q: What does that actually look like?
  ;; A: Well, pretty definitely not like this.
  (let [drawer
        (condp = (:state @(:fsm state))
          :initial-splash draw-initial-splash 
          draw-unknown-state)]
    (drawer state)))



(defn begin
  "Actual graphics thread where everything interesting happens."
  [visual-details]
  (app/start
   {:init init
    :display display
    :reshape reshape}
   visual-details)

  (run-splash visual-details)

  ;; Except that this is an extremely wrong approach.
  (throw (RuntimeException. "Now the cool stuff can happen")))
