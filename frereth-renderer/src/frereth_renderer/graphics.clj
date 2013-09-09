(ns frereth-renderer.graphics
  ;; TODO: If I'm going to take this approach, definitely want to have
  ;; different options about which OpenGL version to use.
  ;; Although this probably makes a lot of sense for what I'm
  ;; using here.
  (:import [org.lwjgl.opengl Display DisplayMode GL11]
           ;; Very torn about using GLU. It seems like a mistake.
           [org.lwjgl.util.glu GLU])
  (:require [clojure.core.async :as async])
  (:gen-class))

(defn driver-version
  "What's available?"
  []
  (GL11/glGetString GL11/GL_VERSION))

(defn init-window
  "Initialize the static window where absolutely everything interesting will happen.
This approach is more than a little dumb...should really be able to create full-screen
windows that fill multiple monitors.
Baby steps. I'm just trying to get that rope thrown across the gorge."
  [{:keys [width height title]}]
  (Display/setDisplayMode (DisplayMode. width height))
  (Display/setTitle title)
  (Display/create))

(defn init-gl
  "Set up the basic framework for rendering.
This is *definitely* not a long-term thing. Each world should be
specifying its own viewport. Most games will probably want multiple
modes for a HUD.
Baby steps."
  [{:keys [width height]}]
  (GL11/glClearColor 0.5 0.0 0.5 0.0)
  (GL11/glMatrixMode GL11/GL_PROJECTION)
  ;; *Definitely* have mixed feelings about using GLU. Isn't that
  ;; basically deprecated?
  (GLU/gluOrtho2D 0.0 width
                  0.0 height)
  (GL11/glMatrixMode GL11/GL_MODELVIEW))

(defn stop []
  (Display/destroy))

(defn build-display
  "Set up the 'main' window."
  [params]
  (init-window params)
  (init-gl params))

(defn draw-basic-triangles
  [{:keys [width height angle]}
   drawer]
  (let [w2 (/ width 2.0)
        h2 (/ height 2.0)]
    (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))
    (GL11/glLoadIdentity)
    (GL11/glTranslatef w2 h2 0)
    (GL11/glRotatef angle 0 0 1)
    (GL11/glScalef 2 2 1)
    (GL11/glBegin GL11/GL_TRIANGLES)
    (drawer)
    (GL11/glEnd)))

(defn draw-colored-vertex 
  [color vertex]
  (GL11/glColor3f (color 0) (color 1) (color 2))
  (GL11/glVertex2i (vertex 0) (vertex 1)))

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
  (* (/ 1 fps) 1000))

(defn run-splash
  "Show an initial splash screen while we're waiting for something interesting"
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
      (when (not (Display/isCloseRequested))
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
                                                     value))))))))))))))
