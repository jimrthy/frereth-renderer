(ns frereth-renderer.application
  (:require [com.stuartsierra.component :as component]
            [frereth-renderer.graphics :as graphics]
            [frereth-renderer.persist.core :as persist]
            [frereth-renderer.session.core :as session]
            [play-clj.core :as play-clj]
            [ribol.core :refer (raise)]
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
         (log/info "Initializing Main Window")
         (try
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
             (log/info "Main window initialized")
             (into this {:listener game
                         :owner app}))
           (catch RuntimeException ex
             (log/error ex "Setting up the Application failed")
             (raise [:app/runtime-initialization
                     {:reason ex}]))
           (catch Exception ex
             (log/error ex "Unexpeted error setting up Application")
             (raise [:app/base-initialization
                     {:reason ex}]))
           (catch Throwable ex
             (log/error ex "*Really* unexpected error setting up Application")
             (raise [:app/thrown-initialization
                     {:reason ex}]))))
  (stop [this]
        (.exit owner)
        (into this {:listener nil
                    :owner nil})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn new-application
  []
  (map->App {}))

