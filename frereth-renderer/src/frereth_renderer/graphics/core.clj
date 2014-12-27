(ns frereth-renderer.graphics.core
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [frereth-renderer.events :as events]
            [frereth-renderer.fsm :as fsm]
            [frereth-renderer.util :as util]
            [play-clj.core :as play-clj]
            [play-clj.g2d :as g2d]
            [play-clj.g3d :as g3d]
            [play-clj.math :as math]
            [play-clj.ui :as play-ui]
            [ribol.core :refer (manage on raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [frereth_renderer.fsm FiniteStateMachine])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defmulti draw
  (fn [state]
     (fsm/current-state (:fsm state))))

(declare build-hud build-main-3d)
;; TODO: How many of these can I import without causing any
;; issues?
(s/defrecord Graphics
    [application  ; frereth.renderer/App
     channel
     client-heartbeat-thread  ; This is a go block
     fsm :- FiniteStateMachine
     entities-hud :- clojure.lang.Atom
     entities-3d :- clojure.lang.Atom
     hud  ; instance of a play-clj Screen
     logging  ; frereth-renderer.logging/Logger
     main-view-3d  ; Screen
     screen-hud :- clojure.lang.Atom
     screen-3d :- clojure.lang.Atom
     session-manager  ; session.manager/SessionManager
     ]
  component/Lifecycle
  (start 
   [this]
   (log/info "Kicking off FSM -- " (util/pretty fsm))
   (try          
     (fsm/send-transition fsm :disconnect)
     (catch RuntimeException ex
       (log/error ex "Sending disconnect request failed")
       (raise [:fsm-transition
               {:reason ex}]))
     (catch Exception ex
       (log/error ex "Sending disconnect failed badly")
       (raise [:fsm-transition
               {:reason ex}]))
     (catch Throwable ex
       (log/error ex "Error requesting disconnect transition")
       (raise [:fsm-transition
               {:reason ex}])))
    (log/info "FSM Disconnected")

    ;; This approach is wrong.
    ;; Each View (controlled by the view-manager)
    ;; needs to have its appropriate screens.
    ;; In all honesty, this should really just be
    ;; choosing which View to draw.

    ;; Except that, given the underlying architecture,
    ;; that isn't right either.

    ;; The problem may really stem from the fact that
    ;; the entire architecture is simply wrong for what
    ;; I need to make happen.
    (let [screen-hud-atom (or screen-hud (atom {}))
          entities-hud-atom (or entities-hud (atom []))
          hud (build-hud screen-hud-atom entities-hud-atom)
          screen-3d-atom (or screen-3d (atom {}))
          entities-3d-atom (or entities-3d (atom []))
          main-view-3d (build-main-3d fsm screen-3d-atom entities-3d-atom)]
      (play-clj/set-screen! (:listener application main-view-3d hud))
      (into this {:channel (async/chan)
                  :screen-hud screen-hud-atom
                  :entities-hud entities-hud-atom
                  :screen-3d screen-3d-atom
                  :entities-3d entities-3d-atom
                  :hud hud
                  :main-view-3d main-view-3d})))
  (stop [this]
    (log/info "Canceling FSM")
    (try          
      (fsm/send-transition fsm :cancel)
      (catch RuntimeException ex
        ;; It's extremely tempted to propogate this, so
        ;; it doesn't get missed.
        ;; TODO: Do that, in debug mode.
        (log/error ex "Failed to cancel the FSM:\n"
                   (util/pretty fsm)
                   "\nError:\n" ex
                   "\nStack Trace:\n" (.getStackTrace ex)
                   "\nMessage: " (.getMessage ex)))
      (catch Exception ex
        ;; It's extremely tempted to propogate this, so
        ;; it doesn't get missed.
        ;; TODO: Do that, in debug mode.
        (log/error ex "Error tring to cancel the FSM:\n"
                   (util/pretty fsm)
                   "\nError:\n" ex
                   "\nStack Trace:\n" (.getStackTrace ex)
                   "\nMessage: " (.getMessage ex)))
      (catch Throwable ex
        ;; It's extremely tempted to propogate this, so
        ;; it doesn't get missed.
        ;; TODO: Do that, in debug mode.
        (log/error ex "Major problem cancelling the FSM:\n"
                   (util/pretty fsm)
                   "\nError:\n" ex
                   "\nStack Trace:\n" (.getStackTrace ex)
                   "\nMessage: " (.getMessage ex))))
    (log/info "Calling reset on the HUD screen atom")
    (reset! screen-hud {})
    (log/info "Calling reset on the HUD entities atom")
    (reset! entities-hud [])
    (log/info "Calling reset on the 3D screen atom")
    (reset! screen-3d {})
    (log/info "Calling reset on the 3D entities atom")
    (reset! entities-3d [])
    (log/info "Stopped")
    (if channel
      (async/close! channel)
      (log/warn "No Visualizer Channel to stop"))
    (into this {:channel nil
                :hud nil
                :main-view-3d nil})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Information
;;; This doesn't actually belong here

(defn driver-version
  "What's available?
Note that penumbra has a get-version that returns a float
version of the same value."
  []
  (play-clj/gl :gl-version))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Initialization

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Event Handlers

(defn reshape
  "Should get called every time the window changes size.
For that matter, it should probably get called every time the
window changes position. In practice, it almost never seems to
get called.
Actually, if my experiments with pen-sample are any indication,
this never gets called."
  [[x y w h] state]
  ;; I have different goals from ztellman. We'll probably
  ;; branch for real here. I need to get his permission.
  ;; I want this to update every time the outer window changes
  ;; state.
  ;; And, periodically, when it changes position.
  ;; This gets ugly
  (comment)(log/trace "Reshape")
  ;; FIXME: This fixed camera isn't appropriate at all.
  ;; It really needs to be set for whichever window is
  ;; currently active.
  ;; But it's a start.
  ;; Besides...this is the vast majority of what init-gl was
  ;; doing for starters.
  (comment (gl/frustum-view 60.0 (/ (double w) h) 1.0 100.0)
           (gl/load-identity))
  (raise :not-implemented)
  state)

;;; FIXME: Initialization/destruction code seems to make more
;;; sense in
;;; its own namespace.

(declare display)
(declare frereth-update)
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
             :update frereth-update}
            visual-details)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Drawing

(defmethod draw :main-life [params]
  (throw (RuntimeException.
          "Draw whatever the client has told us to!")))

(defmethod draw :default [params]
  ;; FIXME: Do something better here.
  ;; Think Sad Mac or BSOD.
  (throw (RuntimeException. (str "Unknown State: " params))))

(defn frereth-update
  "This is a left-over from penumbra. It really doesn't make
a lot of sense in a version based on play-clj.

It might make more sense in a version that's just based on
libgdx, or raw lwjgl.

Called each frame, just before display. This lets me make
things stateful.
An exception that escapes here crashes the entire app.
As in, the window dies, nothing gets displayed, and the app
seems
to keep running, doing absolutely nothing.
This is absolutely unacceptable.
TODO: Wrap this entire thing in an exception handler. If something
goes wrong, switch to an Error State, and draw a Mac Bomb.
There's no excuse for the current sorry state of things, except that
I'm trying to remember/figure out how all the pieces fit together."
  [[delta time] params]
    (comment
           (log/trace "Update callback: " time " -- " params "\nDelta: " delta "\n"          
           (util/pretty params)))
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
                           (log/trace "From update, returning:\n"          
                           (util/pretty updated))
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

;;; Basic Drawing

(defn build-hud
  "Set up the view/screen that goes in front of the 'real' action"
  [screen-atom
   entities-atom]
  (log/debug "Building the HUD around " screen-atom " and " entities-atom)
  (play-clj/defscreen* screen-atom entities-atom
    {:on-show (fn [screen entities]
                ;; Initial setup
                (play-clj/update! screen
                                  :camera (play-clj/orthographic)
                                  :renderer (play-clj/stage))
                (assoc (play-clj.ui/label "0" (play-clj/color :white))
                  :id :fps
                  :x 5))
     :on-render
     (fn [screen entities]
       ;; Called every frame
       (->> (for [entity entities]
              (case (:id entity)
                :fps (doto entity (play-clj.ui/label! :set-text (str (play-clj/game :fps))))
                entity))
            (play-clj/render! screen)))

     :on-key-down
     (fn [screen entities]
       (let [k (:key screen)]
         (when (= k (play-clj/key-code :escape))
           (comment
             ;; This pretty much kills the game loop. Which I absolutely
             ;; do want to protect against
             (raise [:not-implemented
                     {:what "Stop the Application"
                      :how "That's the question"}]))
           (let [component (:component entities)]
             ))))

     :on-resize
     (fn [screen _]
       ;; Surely there's more to be done
       (play-clj/height! screen 768))}))

(defn build-main-3d
  "Set up the part where the 'real' action goes.

This model falls apart fairly quickly. For any given View, I really shouldn't
be trying to take charge of things like the camera settings."
  [fsm
   screen-atom
   entities-atom]
  (play-clj/defscreen* screen-atom entities-atom
    {:on-show 
     (fn [screen entities]
       (play-clj/update! screen
                         :renderer (play-clj.g3d/model-batch)
                         :attributes (let [attr-type (play-clj.g3d/attribute-type :color :ambient-light)
                                           attr (play-clj.g3d/attribute :color attr-type 0.8 0.8 0.8 1)]
                                       (play-clj.g3d/environment :set attr))
                         :camera (doto (play-clj/perspective 75
                                                             (play-clj/game :width)
                                                             (play-clj/game :height))
                                   (play-clj/position! 0 0 3)
                                   (play-clj/direction! 0 0 0)
                                   (play-clj/near! 0.1)
                                   (play-clj/far! 300)))
       (let [attr (play-clj.g3d/attribute! :color :create-diffuse (play-clj/color :blue))
             model-mat (play-clj.g3d/material :set attr)
             model-attrs (bit-or (play-clj/usage :position) (play-clj/usage :normal))
             builder (play-clj.g3d/model-builder)]
         (-> (play-clj.g3d/model-builder! builder
                                          :create-box
                                          2 2 2
                                          model-mat
                                          model-attrs)
             play-clj.g3d/model
             (assoc :x 0 :y 0 :z 0))))

     :on-render
     (fn [screen entities]
       (play-clj/clear! 1 0 1 1)
       (doto screen
         (play-clj/perspective! :rotate-around
                                (play-clj.math/vector-3 0 0 0)
                                (play-clj.math/vector-3 0 1 1)
                                1)
         (play-clj/perspective! :update))
       (play-clj/render! screen entities))}))

;;; Initialization

(defn init
  []
  (map->Graphics {}))
