(ns frereth-renderer.events
  (:require [clojure.core.async :as async]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utility

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

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

