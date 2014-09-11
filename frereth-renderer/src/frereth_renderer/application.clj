(ns frereth-renderer.application
  (:import [com.badlogic.gdx Game]
           [com.badlogic.gdx.backends.lwjgl LwjglApplication])
  (:require [com.stuartsierra.component :as component]
            [frereth-renderer.graphics :as graphics]
            [play-clj.core :as play-clj]
            [schema.core :as s]
            [schema.macros :as sm]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(sm/defrecord Session [left :- s/Int
                       top :- s/Int
                       width :- s/Int
                       height :- s/Int
                       title :- s/Str
                       message-coupling
                       fsm
                       update-function]
  component/Lifecycle
  (start [this]
         ;; TODO: Also remember window positions from last run and reset them here.
         (log/warn "Restore the previous session"))

  (stop [this]
        (log/warn "Save session for restoring next time")))

;;; TODO: Doing this here breaks most of the point behind play-clj,
;;; quite horribly.
;;; At the very least, move it over to the same sort of directory
;;; structure to take advantage of all the cross-platform work
;;; that's happened.
(sm/defrecord Application [listener owner session :- Session]
  component/Lifecycle
  (start [this]
         (let [game (proxy [Game] []
                      ;; This is a convenient way to handle things.
                      ;; Except that it feels wrong.
                      ;; It seems like I should be using the FSM
                      ;; to control transitions from one screen
                      ;; to the next.
                      (create []
                        (play-clj/set-screen! graphics/initial-splash)))
          title (:title session)
          width (:width session)
          height (:height session)
          app (LwjglApplication. game title width height)]
      (into this {:listener game
                  :owner app})))
  (stop [this]
        (.exit owner)
        (into this {:listener nil
                    :owner nil})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn new-application
  []
  (map->Application []))

(defn new-session
  "This is pretty horribly over-simplified.
But it's a start
"
  [{:keys [width height title update-function]
    :or {:width 1024
         :height 768
         :title "Frereth"}}]
  (map->Session {:width width
                 :height height
                 :title title}))

