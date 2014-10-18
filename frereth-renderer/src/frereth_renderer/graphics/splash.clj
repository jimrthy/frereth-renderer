(ns frereth-renderer.graphics.splash
  (require [frereth-renderer.graphics.core :as graphics :refer (draw)]
           [ribol.core :refer (raise)]
           [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Splash Triangles

(defn draw-basic-triangles
  [{:keys [width height angle] :or {width 1 height 1 angle 0}}
   drawer]
  (comment (log/trace "Drawing a Basic Triangle"          
           ;; FIXME: Desperately need to redirect pprint's
           ;; output to a string and log it instead of printing.
           (util/pretty [width height angle drawer])))

  (let [w2 (/ width 2.0)
        h2 (/ height 2.0)]
    ;;(gl/translate w2 h2 0)
    (comment (gl/translate 0 -1.5 -3)
             (gl/rotate angle 0 0 1)
             (gl/scale 2 2 1)
             (gl/draw-triangles (drawer)))
    (raise :not-implemented)))

(defn draw-colored-vertex 
  [color vertex]
  (comment (log/trace "Drawing a " color " vertex at " vertex))
  (comment (gl/color (color 0) (color 1) (color 2))
           (gl/vertex (vertex 0) (vertex 1)))
  (raise :not-implemented))

(defn draw-multi-colored-triangle
  [[color1 color2 color3] [vertex1 vertex2 vertex3]]
  (draw-colored-vertex color1 vertex1)
  (draw-colored-vertex color2 vertex2)
  (draw-colored-vertex color3 vertex3))

(defn draw-splash-triangle
  [params intensity]
  (letfn [(minimalist-triangle []
            (comment (log/trace "Drawing a Splash"))
            (let [color1 [intensity 0.0 0.0]
                  color2 [0.0 intensity 0.0]
                  color3 [0.0 0.0 intensity]
                  vertex1 [1 0]
                  vertex2 [-1 0]
                  vertex3 [0 1.5]
                  ]
              (draw-multi-colored-triangle
               [color1 color2 color3]
               [vertex1 vertex2 vertex3])))]
    (draw-basic-triangles params minimalist-triangle)))

(defmethod draw :__dead[params]
  ;; Initializing...absolutely nothing interesting has happened yet
  [params]
  ;; FIXME: Fill the screen with whitespace, or something vaguely
  ;; interesting
  (comment) (log/trace "Drawing dead")
  (draw-splash-triangle params 0.5))

(defmethod draw :disconnected [params]
  [params]
  ;; Rendering subsystem is up and ready to go. Waiting to hear from the client.
  (comment (log/trace "draw-initial-splash"))
  (draw-splash-triangle params 1.0))

(defn draw-secondary-splash [params]
  (let [angle (:angle params)
        radians (Math/toRadians angle)
        cos (Math/cos radians)
        intensity (Math/abs cos)]
    (draw-splash-triangle params intensity)))

(defmethod draw :waiting-for-server [params]
  [params]
  ;; Have connected to the client. Waiting to hear back from the server
  (draw-secondary-splash params))

(defmethod draw :server-connected [params]
  [params]
  ;; Client's connected to the server. Just waiting for the handshake to
  ;; finish so we can start drawing whatever the server wants.
  (draw-secondary-splash (into params
                               {:angle (* (:angle params) 2)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Action

(defn update-initial-splash
  "Rotate the triangle periodically"
  [{:keys [angle delta-t] 
    :or {angle 0 delta-t 0}
    :as params}]
  (comment
    (log/trace "Initial Update State:\n"          
              (util/pretty params)))
  (let [next-angle (+ (* delta-t 8) angle)
        next-angle (if (>= next-angle 360.0)
                     (- next-angle 360.0)
                     next-angle)]
    (into params {:angle next-angle})))

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
                 controller (-> params :messaging :command)]
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

