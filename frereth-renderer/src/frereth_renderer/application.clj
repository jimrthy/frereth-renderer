(ns frereth-renderer.application
  (:require [com.stuartsierra.component :as component]
            [frereth-renderer.graphics :as graphics]
            [frereth-renderer.persist.core :as persist]
            [frereth-renderer.session.core :as session]
            [play-clj.core :as play-clj]
            [schema.core :as s]
            [schema.macros :as sm]
            [taoensso.timbre :as log])
  (:import [com.badlogic.gdx Application Game]
           [com.badlogic.gdx.backends.lwjgl
            LwjglApplication]
           [frereth_renderer.session.core Session]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;;; TODO: Doing this here breaks most of the point behind play-clj,
;;; quite horribly.
;;; At the very least, move it over to the same sort of directory
;;; structure to take advantage of all the cross-platform work
;;; that's happened.
(sm/defrecord App [listener owner :- Application session :- Session]
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
  (map->App {}))

