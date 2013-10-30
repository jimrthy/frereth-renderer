(ns frereth-renderer.graphics
  (:require [clojure.core.async :as async]
            [clojure.pprint :refer (pprint)]
            [frereth-renderer.fsm :as fsm]
            [penumbra.app :as app]
            [penumbra.app.core :as core]
            [penumbra.opengl :as gl]
            ;;[snapshot.core]
            [taoensso.timbre :as timbre
             :refer [ trace debug info warn error fatal spy with-log-level]])
  (:gen-class))

;;;; FIXME: This namespace is getting too big. How much can I split out
;;;; into smaller pieces?

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
  ;; There's absolutely nothing happening in here now.
  (throw (RuntimeException. "Obsolete"))

  (trace "Initializing window")
  (spy state)
  ;; Have to pass in the window in question.
  ;; As annoying as it is, that's the way Protocols work.
  ;; Changing something that basic in Penumbra means very
  ;; breaking API changes. Probably doesn't matter, since I
  ;; seriously doubt anyone else is using this. But not
  ;; something that I have time for just now.
  ;; FIXME: Do I seriously not have a window to hand to this?
  (comment (app/vsync! nil true))

  ;; These don't seem to have penumbra equivalents (though that's probably just
  ;; because I haven't dug deep enough yet).
  ;; Whatever. I desperately need to do something along these lines.
  (comment (Display/setDisplayMode (DisplayMode. width height))
           (Display/setTitle title)
           (Display/create))
  state)

(defn init-fsm
  "This doesn't particularly belong here. Except that the two seem
strongly coupled inherently. Probably a symptom of the bigger problem
that there's just too much going on in here."
  []
  (fsm/init {:disconnected
             {:client-connect-without-server [nil :waiting-for-server]
              :client-connect-with-server    [nil :waiting-for-home-page]
              :client-connect-with-home-page [nil :main-life]}
             :waiting-for-server
             {:client-disconnect             [nil :disconnected]}
             :server-connected
             {:waiting-for-home-page         [nil :main-life]
              :client-disconnect             [nil :disconnected]}
             :main-life
             {:client-disconnect             [nil :disconnected]}}))

(defn configure-windowing
  "Penumbra's (init)"
  ([]
     ;; Surely there are some default parameters that make sense.
     ;; They should really come from whatever's doing the init, though.
     ;; Or maybe it should be a mixture of both. Whatever. Worry about
     ;; it later.
     (configure-windowing {}))
  ([params]
     (trace "Configuring the Window. Params:\n" params)
     (app/vsync! true)
     (gl/clear-color 0.5 0.0 0.5 0.0)
     params))

(defn reshape
  "Should get called every time the window changes size.
For that matter, it should probably get called every time the window
changes position. In practice, it almost never seems to get called.
Actually, if my experiments with pen-sample are any indication, this
never gets called."
  [[x y w h] state]
  ;; I wonder whether this has something to do with LWJGL changes
  (throw (RuntimeException. "Oops, I was wrong"))

  ;; FIXME: This fixed camera isn't appropriate at all.
  ;; It really needs to be set for whichever window is currently active.
  ;; But it's a start.
  ;; Besides...this is the vast majority of what init-gl was doing for starters.
  (gl/frustum-view 60.0 (/ (double w) h) 1.0 100.0)
  (gl/load-identity)
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
  (let [channel (-> state :messaging :local-mq)]
    (async/go (async/>! message))))

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
  (notify-input state {:what :mouse
                       :which (into details which)}))

(defn mouse-drag [[dx dy] [x y] button state]
  (notify-mouse-input state :drag {:start [x y]
                                   :delta [dx dy]
                                   :button button}))

(defn mouse-move [[dx dy] [x y] state]
  (notify-mouse-input state :move {:start [x y]
                                   :delta [dx dy]}))

(defn mouse-button [state button location which]
  (notify-mouse-input state which {:location location
                                   :button button}))

(defn mouse-click [location button state]
  (mouse-button state button location :click))

(defn mouse-down [location button state]
  (mouse-button state button location :down))

(defn mouse-up [location button state]
  (mouse-button state button location :up))

(defn close [state]
  (throw (RuntimeException. "What does close message actually mean?")))

;;; FIXME: Initialization/destruction code seems to make more sense in
;;; its own namespace.

(declare display)
(declare update)
(defn begin-eye-candy-thread
  "Graphics first and foremost: the user needs eye candy ASAP.
This makes that happen"
  [visual-details]
  (info "Kicking off penumbra window")
  (app/start
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
   visual-details))

(defn begin-communications
  " Actually updating things isn't as interesting [at first] or [quite]
as vital...but it's a very close second in both
categories.
OTOH, this really belongs elsewhere."
  [state]
  (let [control-channel (-> state :messaging deref :local-mq)
        fsm-atom (:fsm state)]
    (trace "****************************************************
Beginning Communications
State: " state "\n\nMessaging: " (:messaging state)
           "\n\nControl Channel: " control-channel "\n\nFSM Atom: " fsm-atom
           "\n****************************************************")
    (async/go
     (loop [msg (async/<! control-channel)]
       (trace "Control Message:\n" msg)

       ;; FIXME: Need a "quit" message.
       ;; This approach misses quite a few points, I think.
       ;; This pieces of the FSM should be for very coarsely-
       ;; grained transitions...
       ;; then again, maybe my entire picture of the architecture
       ;; is horribly flawed.
       ;; TODO: Where's the actual communication happening?
       ;; All I really care about right here, right now is
       ;; establishing the heartbeat connection.
       (let [next-state (fsm/transition! @fsm-atom msg true)]
         ;; TODO: I don't think this is really even all that close
         ;; to what I want.
         (when-not (= next-state :__dead)
           (recur (async/<! control-channel)))))

     (info "Communications loop exiting")
     ;; TODO: Kill the window!!
     (let [terminal-channel (-> state :messaging deref :terminator)]
       ;; Realistically, I want this to be synchronous.
       ;; Can that happen inside a go block?
       ;; Oh well. It shouldn't matter all that much.
       (async/>! terminal-channel :game-over)))
    (trace "Communications Thread set up")))

(defn begin
  "Kick off the threads where everything interesting happens."
  [visual-details]

  ;; Note that this probably won't work on Mac. Or maybe it will...it'll
  ;; be interesting to see. (I've seen lots of posts complaining about trying to
  ;; get Cocoa apps doing anything when the graphics try to happen on anything
  ;; except the main thread)
  (trace "Starting graphics thread")
  (async/thread
    (begin-eye-candy-thread visual-details))
  (trace "Starting communications thread")
  (begin-communications visual-details)
  (trace "Communications begun"))

(defn init []
  (let [system-state (init-fsm)]
    ;; Do have an agent here.
    (comment)
    (info "Initial FSM state: " system-state)
    {:renderer nil
     :fsm system-state}))

(defn start [graphics messaging]
  (info "Starting graphics system")
  (let [;; TODO: Don't use magic numbers.
        ;; TODO: Remember window positions from last run and reset them here.
        ;; N.B.: That really means a custom classloader. Which must happen
        ;; eventually anyway.
        ;; Q: Why? On both counts? (i.e. Why would I need a custom classloader
        ;; for remembering last position, much less needing it eventually?)
        ;; I just want to put it off as long as possible.
        visual-details {:width 1024
                        :height 768
                        :title "Frereth"
                        :messaging messaging
                        :fsm (:fsm graphics)}]
    (begin visual-details))

  (comment (trace "**********************************************************
Kicking off the fsm. Original agent:\n" (:fsm graphics)
"\nOriginal agent state:\n" @(:fsm graphics)
"\n***********************************************************"))

  (let [renderer-state (:renderer graphics)
        ;;windowing-state (init-gl renderer-state)
        ]
    (trace "Updating the FSM")
    (fsm/start! (:fsm graphics) :disconnected)
    (trace "Graphics Started")
    graphics))

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


;;; FIXME: The triangle pieces would make a lot of sense in their own namespace
(defn draw-basic-triangles
  [{:keys [width height angle] :or {width 1 height 1 angle 0}}
   drawer]
  (comment (println "Drawing a Basic Triangle")
           (pprint [width height angle drawer]))
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
  (comment (trace "Drawing dead"))
  (draw-splash-triangle params 0.5))

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

;; Obsolete...except that it totally isn't.
;; FIXME: Is any of this worth trying to save?
(defn update
  "Called each frame, just before display. This lets me make things stateful."
  [[delta time] params]
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
                 controller (-> params :messaging :local-mq)]
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

  ;; I *am* getting here pretty frequently
  ;; FIXME: Debugging only: check where FSM is hosed
  (comment (if-let [fsm-atom (:fsm state)]
             (if-let [fsm @fsm-atom]
               (if-let [actual-state (:state fsm)]
                 (trace "Have a state: " actual-state)
                 (error "Missing state!"))
               (error "Missing FSM in the atom!"))
             (error "Missing FSM atom??")))

  (let [state (fsm/current-state (:fsm state))
        ;; FIXME: This is more than a little horrid.
        ;; Q: How can I improve it? Esp. given the constraint that
        ;; clojure multimethods dispatch slowly.
        ;; Is this a situation where protocols might actually be
        ;; appropriate?
        ;; A: For now, that's premature optimization. Switching
        ;; to a multimethod is probably an excellent idea, though.
        drawer
        (condp = state
          :__dead draw-dead
          :disconnected draw-initial-splash
          :waiting-for-server draw-secondary-splash
          :server-connected draw-final-splash
          :main-life draw-main
          draw-unknown-state)]
    (comment (trace "Current Situation: " state))
    (drawer state)

    ;; Signal to refresh and redraw the next frame.
    ;; Overall, I probably don't want to do this. There will be
    ;; screens that are basically static and don't need to be redrawn
    ;; very often.
    ;; That's an optimization for the future. For now, I need to get
    ;; something minimalist working.
    (app/repaint!)))
