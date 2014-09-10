(ns frereth-renderer.graphics
  (:refer-clojure :exclude [run!])
  (:require [clojure.core.async :as async]
            [clojure.pprint :refer (pprint)]
            [com.stuartsierra.component :as component]
            [frereth-renderer.fsm :as fsm]
            #_[penumbra.app :as app]
            #_[penumbra.app.core :as core]
            #_[penumbra.opengl :as gl]
            [play-clj.core :as play-clj]
            [play-clj.g2d :as g2d]
            [play-clj.g3d :as g3d]
            [play-clj.math :as math]
            [play-clj.ui :as ui]
            [ribol.core :refer (manage on raise)]
            [taoensso.timbre :as log]))

;;;; FIXME: This namespace is getting too big. How much can I split out
;;;; into smaller pieces?

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defrecord Visualizer [channel]
  component/Lifecycle
  (start
    [this]
    (assoc this :channel (async/chan)))
  (stop
    [this]
    (async/close! channel)
    (assoc this :channel nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Information
;;; This doesn't actually belong here

(defn driver-version
  "What's available?
Note that penumbra has a get-version that returns a float version of the same value."
  []
  (play-clj/gl :gl-version))

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

;;; Q: Do I really want to send this sort of low-level communication to
;;; the renderer?
;;; It makes some sort of sense, if that's where all the logic happens.
;;; But, e.g. it seems like it shouldn't have any idea about the worldview matrix
;;; to do translations from window coordinates into world coordinates for
;;; determining a click location.
;;; A: This really needs to be determined by the app in question. For some,
;;; just sending the raw value in might make the most sense.
;;; For others...let it pass in script to handle the logic here.
;;; That implies a tighter architectual coupling than I like. How can I
;;; avoid that?
(defn notify-input [state message]
  (comment (log/trace "Input notification:\n" message "\n" state))
  (if-let [msg (:messaging state)]
    (if-let [channel (:user-input @msg)]
      (do (async/go (async/>! channel message))
          state)
      (log/error "Missing Message Queue channel for input notification\n("
             msg ")"))
    (log/error "State has no messaging member to submit input notification\n("
           state ")\nMessage:\n" message)))

(defn notify-key-input [state key which]
  (notify-input state {:what :key
                       :which which}))

(defn key-press [key state]
  (notify-key-input state key :press))

(defn key-release [key state]
  (notify-key-input state key :release))

(defn key-type [key state]
  (notify-key-input state key :type))

(defn notify-mouse-input [state which details]
  (comment (let [msg (str "Mouse Input\nWhich: " which "\nDetails:\n" details "\nState:\n" state)]
             (log/trace msg)))
  (notify-input state {:what :mouse
                       :which (into details which)}))

(defn mouse-drag [[dx dy] [x y] button state]
  (notify-mouse-input state {:message :drag}
                      {:start [x y]
                       :delta [dx dy]
                       :button button}))

(defn mouse-move [[dx dy] [x y] state]
  (comment (let [msg (str "Mouse Move @ (" x ", " y ")
Delta: (" dx ", " dy ")
State:\n" state)]
             (log/debug msg)))
  (notify-mouse-input state {:message :move}
                      {:start [x y]
                       :delta [dx dy]}))

(defn mouse-button [state button location which]
  (notify-mouse-input state which {:location location
                                   :button button}))

(defn mouse-click [location button state]
  (mouse-button state button location {:message :click}))

(defn mouse-down [location button state]
  (mouse-button state button location {:message :down}))

(defn mouse-up [location button state]
  (mouse-button state button location {:message :up}))

(defn close [state]
  ;; Q: "What does close message actually mean?"
  ;; A: At least part of it is that the window is closing. Duh.
  (log/info "Closing app window.")
  ;; TODO: Save size/shape to restore next time around
  )

;;; FIXME: Initialization/destruction code seems to make more sense in
;;; its own namespace.

(declare display)
(declare update)
(defn begin-eye-candy-thread
  "Graphics first and foremost: the user needs eye candy ASAP.
This makes that happen"
  [visual-details]
  (log/info "Kicking off penumbra window")
  (raise :not-implemented)
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

(defn begin-communications
  " Actually updating things isn't as interesting [at first] or [quite]
as vital...but it's a very close second in both
categories.
OTOH, this really belongs elsewhere."
  [state]
  (let [control-channel (-> state :messaging deref :command)
        fsm-atom (:fsm state)]
    (log/trace "\n****************************************************
Initializing Communications
State: " state "\nMessaging: " (:messaging state)
           "\nControl Channel: " control-channel "\nFSM Atom: " fsm-atom
           "\n****************************************************")
    (let [communications-thread
          (async/go
           (loop [msg (async/<! control-channel)]
             ;; Check for channel closed
             (when-not (nil? msg)
               (log/trace "Control Message:\n" msg)

               ;; FIXME: Need a "quit" message.
               ;; This approach misses quite a few points, I think.
               ;; This pieces of the FSM should be for very coarsely-
               ;; grained transitions...
               ;; then again, maybe my entire picture of the architecture
               ;; is horribly flawed.
               ;; TODO: Where's the actual communication happening?
               ;; All I really care about right here, right now is
               ;; establishing the heartbeat connection.

               (log/debug "Communications Loop Received\n" 
                             msg "\nfrom control channel")
               (throw (RuntimeException. "Start here"))

               ;; This really isn't good enough. This also has to handle responses
               ;; to UI requests. I'm torn between using yet another channel
               ;; (to where?) for this and using penumbra's message queue.
               ;; Then again, maybe requiring client apps to work with the
               ;; FSM makes sense...except that now we're really talking about
               ;; a multiple secondary FSMs which I have absolutely no control
               ;; over. That doesn't exactly seem like a good recipe for a
               ;; "happy path"       
               (let [next-state (fsm/transition! @fsm-atom msg true)]
                 ;; TODO: I don't think this is really even all that close
                 ;; to what I want.
                 (when-not (= next-state :__dead)
                   (recur (async/<! control-channel))))))
           ;; Doesn't hurt to close it twice, does it?
           (async/close! control-channel)

           (log/info "Communications loop exiting")
           ;; TODO: Kill the window!!
           (let [terminal-channel (-> state :messaging deref :terminator)]
             ;; Realistically, I want this to be synchronous.
             ;; Can that happen inside a go block?
             ;; Oh well. It shouldn't matter all that much.
             (async/>! terminal-channel :game-over)))]
      (log/trace "Communications Thread set up")
      communications-thread)))

(defn begin
  "Kick off the threads where everything interesting happens."
  [visual-details]

  ;; Note that this probably won't work on Mac. Or maybe it will...it'll
  ;; be interesting to see. (I've seen lots of posts complaining about trying to
  ;; get Cocoa apps doing anything when the graphics try to happen on anything
  ;; except the main thread)
  (log/trace "Starting graphics thread")
  (let [eye-candy-thread
        (async/thread
         (begin-eye-candy-thread visual-details))]
    (log/trace "Starting communications thread")
    (let [communications-thread
          (begin-communications visual-details)]
      (log/trace "Communications begun")
      {:communications communications-thread
       :graphics eye-candy-thread})))

;; Don't want to declare this here. Really shouldn't be calling it directly
;; at all. Honestly, need something like a var that I can override with
;; the current view.
;; Then again, that should probably be a member of the "global" state that's
;; getting created below in visual-details.
;; Then I should be able to update that member as needed from the communications
;; thread from the client. It probably does not make sense to update it
;; based on anything that would happen on the renderer side, unless I decide
;; that it makes sense to run "window managers" here.
;; So plan on that, for now.
(declare update-initial-splash)
(defn restore-last-session
  "This is horribly over-simplified. But it's a start."
  [messaging graphics]
   {:width 1024
    :height 768
    :title "Frereth"
    :messaging messaging
    :fsm (:fsm graphics)
    ;; This next is pretty much totally invalid long-term.
    ;; But it's a decent baby step.
    :update-function update-initial-splash})

(defn start [graphics messaging]
  (log/info "Starting graphics system")
  (let [;; TODO: Don't use magic numbers.
        ;; TODO: Remember window positions from last run and reset them here.
        ;; N.B.: That really means a custom classloader. Which must happen
        ;; eventually anyway.
        ;; Q: Why? On both counts? (i.e. Why would I need a custom classloader
        ;; for remembering last position, much less needing it eventually?)
        ;; I just want to put it off as long as possible.
        visual-details (restore-last-session messaging graphics)
        background-threads (begin visual-details)]

    (comment) (log/trace "**********************************************************
Kicking off the fsm. Original agent:\n" (:fsm graphics)
"\nOriginal agent state:\n" @(:fsm graphics)
"\n***********************************************************")

    (let [renderer-state (:renderer graphics)
          ;;windowing-state (init-gl renderer-state)
          ]
      (log/trace "Updating the FSM")
      ;; I've moved control of the FSM into its own component.
      ;; The pieces in here don't mesh well with the Components
      ;; library in general.
      (raise [:not-implemented
              {:reason "Next line doesn't match Components"
               :todo "Start here"}])
      (fsm/start! (:fsm graphics) :disconnected)
      (log/trace "Graphics Started")
      (into  graphics {:background-threads background-threads}))))

(defn stop [universe]
  ;; FIXME: Is there anything I can do here?
  ;; (That's a pretty vital requirement)
  
  ;; FIXME: It would be much better to pass in the actual window(s)
  ;; that I want to destroy.
  ;; Then again, that may be totally pointless until/if lwjgl
  ;; gets around to actually switching to that sort of API.
  ;; This is the sort of quandary that makes me wish jogl were
  ;; less finicky about getting installed.
  ;; I don't think I want this.
  ;; TODO: The app has a :destroy! key that points to a function
  ;; that looks suspiciously as though it's what I actually want.
  (comment (core/destroy! (into universe
                                ;; Very tempting to close the window. Actually,
                                ;; really must do that if I want to reclaim resources
                                ;; so I can reset them.
                                ;; That means expanding penumbra's API.
                                ;; TODO: Make that happen.
                                (fsm/stop (-> universe :graphics :fsm))))))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn new-visualizer
  []
  (map->Visualizer {}))
