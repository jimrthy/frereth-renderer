(ns frereth-renderer.graphics
  (:require [clojure.core.async :as async]
            [clojure.pprint :refer (pprint)]
            [com.stuartsierra.component :as component]
            [frereth-renderer.events :as events]
            [frereth-renderer.fsm :as fsm]
            #_[penumbra.app :as app]
            #_[penumbra.app.core :as core]
            #_[penumbra.opengl :as gl]
            [play-clj.core :as play-clj]
            [play-clj.g2d :as g2d]
            [play-clj.g3d :as g3d]
            [play-clj.math :as math]
            [play-clj.ui :as play-ui]
            [ribol.core :refer (manage on raise)]
            [schema.core :as s]
            [schema.macros :as sm]
            [taoensso.timbre :as log]))

;;;; FIXME: This namespace is getting too big. How much can I split out
;;;; into smaller pieces?

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;;; Q: What's the actual difference between these?

(defrecord Visualizer [channel logging session]
  component/Lifecycle
  (start
    [this]
    (log/info "Starting graphics system")
    
    (assoc this :channel (async/chan)))
  (stop
    [this]
    (async/close! channel)
    (assoc this :channel nil)))

(defrecord Graphics
    [fsm client-heartbeat-thread]
  component/Lifecycle
  (start [this]
    (fsm/send-transition fsm :disconnect)
    this)
  (stop [this]
    (fsm/send-transition fsm :cancel)
    this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Information
;;; This doesn't actually belong here

(defn driver-version
  "What's available?
Note that penumbra has a get-version that returns a float version of the same value."
  []
  (play-clj/gl :gl-version))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Initialization

(defn configure-windowing
  "Penumbra's (init)"
  ([]
     ;; Surely there are some default parameters that make sense.
     ;; They should really come from whatever's doing the init, though.
     ;; Or maybe it should be a mixture of both. Whatever. Worry about
     ;; it later.
     (configure-windowing {}))
  ([params]
     (log/trace "Configuring the Window. Params:\n" params)
     (comment (app/vsync! true))

     ;; I don't want to do this!!
     ;; Each client/world needs to set up its own viewing matrix.
     ;; Until I get to that point, I need a basic sample idea that
     ;; pretends to do what they need, if only because I want some
     ;; sort of visual feedback.
     ;; This doesn't seem to make any actual difference. As a bonus,
     ;; closing the window on Windows doesn't actually work. I'm
     ;; not getting any feedback about the error, either.
     (comment (gl/clear-color 0.5 0.0 0.5 0.0)
              (gl/frustum-view 60.0 (/ (double 4) 3) 1.0 100.0)
              (gl/load-identity))
     (raise :not-implemented)

     params))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Event Handlers

(defn reshape
  "Should get called every time the window changes size.
For that matter, it should probably get called every time the window
changes position. In practice, it almost never seems to get called.
Actually, if my experiments with pen-sample are any indication, this
never gets called."
  [[x y w h] state]
  ;; I have different goals from ztellman. We'll probably branch for real
  ;; here. I need to get his permission.
  ;; I want this to update every time the outer window changes state.
  ;; And, periodically, when it changes position.
  ;; This gets ugly
  (comment)(log/trace "Reshape")
  ;; FIXME: This fixed camera isn't appropriate at all.
  ;; It really needs to be set for whichever window is currently active.
  ;; But it's a start.
  ;; Besides...this is the vast majority of what init-gl was doing for starters.
  (comment (gl/frustum-view 60.0 (/ (double w) h) 1.0 100.0)
           (gl/load-identity))
  (raise :not-implemented)
  state)

;;; FIXME: Initialization/destruction code seems to make more sense in
;;; its own namespace.

(declare display)
(declare update)
(defn begin-eye-candy-thread
  "Graphics first and foremost: the user needs eye candy ASAP.
This makes that happen"
  [visual-details stopper]
  (log/info "Kicking off penumbra window")
  (raise :obsolete)
  (comment (app/start
            {:close close
             :display display
             :init configure-windowing
             :key-press key-press
             :key-release key-release
             :key-type key-type
             :mouse-click mouse-click
             :mouse-down mouse-down
             :mouse-drag mouse-drag
             :mouse-move mouse-move
             :mouse-up mouse-up
             :reshape reshape
             :update update}
            visual-details)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;; Drawing
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;; FIXME: The triangle pieces would make a lot of sense in their own namespace
(defn draw-basic-triangles
  [{:keys [width height angle] :or {width 1 height 1 angle 0}}
   drawer]
  (comment (log/trace "Drawing a Basic Triangle")
           ;; FIXME: Desperately need to redirect pprint's
           ;; output to a string and log it instead of printing.
           (pprint [width height angle drawer]))

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

(defmulti draw
  (fn [state]
     (fsm/current-state (:fsm state))))

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

(defmethod draw :main-life [params]
  (throw (RuntimeException. "Draw whatever the client has told us to!")))

(defmethod draw :default [params]
  ;; FIXME: Do something better here.
  ;; Think Sad Mac or BSOD.
  (throw (RuntimeException. (str "Unknown State: " params))))

(defn update-initial-splash
  "Rotate the triangle periodically"
  [{:keys [angle delta-t] 
    :or {angle 0 delta-t 0}
    :as params}]
  (comment
    (log/trace "Initial Update State:")
    (pprint params))
  (let [next-angle (+ (* delta-t 8) angle)
        next-angle (if (>= next-angle 360.0)
                     (- next-angle 360.0)
                     next-angle)]
    (into params {:angle next-angle})))

(defn update
  "Called each frame, just before display. This lets me make things stateful.
An exception that escapes here crashes the entire app.
As in, the window dies, nothing gets displayed, and the app seems
to keep running, doing absolutely nothing.
This is absolutely unacceptable.
TODO: Wrap this entire thing in an exception handler. If something
goes wrong, switch to an Error State, and draw a Mac Bomb.
There's no excuse for the current sorry state of things, except that
I'm trying to remember/figure out how all the pieces fit together."
  [[delta time] params]
    (comment
           (log/trace "Update callback: " time " -- " params "\nDelta: " delta)
           (pprint params))
  (manage
   ;; this value was being set in run-splash.
   ;; Since that is no longer getting called, this no longer gets
   ;; set. Duh.
    (if-let [actual-update (:update-function params)]
      (do
        (let [initial (into params {:delta-t delta})]
          ;; This seems even more debatable than catching a
          ;; base Exception. This is the TopLevel. Anything
          ;; could have gone wrong underneath. I need to verify
          ;; that that part runs OK.
          ;; And I need to let the user know that something has
          ;; gone wrong.
          ;; And, honestly, if an error has escaped up to this
          ;; level, whatever let it escape was faulty and needs
          ;; to die.
          ;; I think some major architecture questions are
          ;; involved here, except that it's probably really
          ;; something stupid that I'm borking and just need
          ;; to trace down and fix...making sure that user
          ;; errors can't ever get to this level.
          (if-let [updated (actual-update initial)]
            (do
              (comment (if-let [drawer (:draw-function updated)]
                         (do
                           ;; Does the return value of the drawer matter?
                           (drawer updated)
                           (log/trace "From update, returning:")
                           (pprint updated)
                           updated)
                         (do
                           (comment) (raise {:error "Update: No draw-function" 
                                             :params params})
                           (comment (throw (RuntimeException. "Missing drawing function"))))))
              ;; Actually, this should not be calling draw.
              (comment (draw updated))
              updated)
            (do (raise {:error "Update: nothing updated"
                        :params params})
                (comment (throw (RuntimeException. "Missing updated"))))))
        ;; WTF? This seems to be causing an exception because a 
        ;; PersistentArrayMap cannot be cast to java.lang.Throwable.
        ;; Isn't that the entire point to slingshot??
        )
      (do
        ;; Q: Do I actually care about this? I don't think I do.
        ;; Well, at least, not after I figure out why my current
        ;; incarnation is a complete and total FAIL.
        (let [obnoxious-message "**************************************************
*
* Look at me!!! <-----------------
*
******************************************************"]
                    (log/warn obnoxious-message))
        ;; If there's no update function...oh well.
        ;; TODO: It should really be up to the client to install the
        ;; appropriate function that should get called here, though it shouldn't
        ;; have a clue about what's actually happening.
        (comment (raise {:error "Missing update function in\n"
                         :params params}))))
    ;; TODO: Error handling here seems problematic.
    ;; Then again, it seems a whole lot nicer than letting any errors
    ;; escape.
    ;; TODO: At the very least, this seems pretty much exactly what Dire was
    ;; designed to handle.
    (on :error [error params]
        (log/error (str error ": " params)))
    (catch RuntimeException e
      (log/error e)
      (raise [:normal-update-failure {:reason e}]))
    (catch Exception e
      ;; I'm very strongly inclined to catch absolutely
      ;; anything that went wrong here and just log/swallow
      ;; it.
      ;; As it stands, this is the equivalent of a Windows
      ;; BSOD for pretty much anything that might have gone
      ;; wrong.
      ;; Which really means low-level hardware issues.
      ;; Those probably do need to bubble up.
      (log/error e)
      (raise [:unexpected-update-failure {:reason e}])))
  ;; TODO: This almost definitely needs to return the updated state.
  ;; I'm guessing that the error handling is ruining that.
  )

(defn fps->millis
  "Silly utilitiy function. At x frames per second, each individual
frame should be on the screen for y milliseconds.
FIXME: This should go away completely. Penumbra already has
utility functions that handle this better.

I think what I'm looking for there is update callbacks:
penumbra.app/periodic-update!
which takes a frequency and callback-fn
where frequency is the # of times/second that callback-fn
should be called."
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

(defn display
  "Draw the next frame."
  [[delta time] state]
  ;; In a nutshell, I need an FSM:
  ;; 1) Show basic splash
  ;; 2) After we're talking to the client, switch to a second splash
  ;; 3) When the client starts telling us what to draw, switch to that.
  ;; Q: What does that actually look like?
  ;; A: Well, pretty definitely not like this.

  ;; I *am* getting here pretty frequently
  ;; FIXME: Debugging only: check where FSM is hosed
  (if-let [fsm-atom (:fsm state)]
    (if-let [fsm @fsm-atom]
      (if-let [actual-state (:state fsm)]
        (do 
          (comment (log/trace "Have a state: " actual-state)))
        (log/error "Missing state!"))
      (log/error "Missing FSM in the atom!"))
    (raise {:error :missing-fsm
            :state state
            :message "Missing FSM atom??"}))
  (draw state)
  (comment (app/repaint!)))

;;; This strongly feels like it violates the entire
;;; Components contract. But this is a defonce, so
;;; it doesn't seem totally awful.
;;; Q: How can I make this reset?
(comment (defgame frereth-renderer
           :on-create
           (fn [this]
             (set-screen! this ))))
;;; A: That creates a proxy for com.badlogic.gdx.Game,
;;; calling the on-create event during its (create)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

;;; Basic Drawing

;; Used from within application
(play-clj/defscreen initial-splash
  :on-show
  (fn [screen entities]
    (play-clj/update! screen :renderer (play-clj/stage))
    (play-ui/label "Loading..." (play-clj/color :yellow)))

  :on-render
  (fn [screen entities]
    (play-clj/clear!)
    (play-clj/render! screen entities)))

;;; Initialization (these should really all go away)

(defn new-visualizer
  []
  (map->Visualizer {}))

(defn init
  []
  (map->Graphics {}))
