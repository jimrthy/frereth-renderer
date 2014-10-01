(ns frereth-renderer.application
  (:require [clojure.pprint :refer (pprint)]
            [clojure.repl :refer (pst)]
            [com.stuartsierra.component :as component]
            [frereth-renderer.graphics :as graphics]
            [frereth-renderer.persist.core :as persist]
            [frereth-renderer.session.core :as session]
            [play-clj.core :as play-clj]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [com.badlogic.gdx Application Game]
           [com.badlogic.gdx.backends.lwjgl LwjglApplication]
           [frereth_renderer.graphics Graphics]
           [frereth_renderer.session.core Session]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(s/defrecord MainListener [listener :- Game graphics :- Graphics]
  component/Lifecycle
  (start
   [this]
   (log/info "Initializing Main Window")
   (try
   (let [hud (:hud graphics)
         main-view-3d (:main-view-3d graphics)]
     (let [game
           (if-not listener
             (proxy [Game] []
               ;; This is a convenient way to handle things.
               ;; Except that it feels wrong.
               ;; It seems like I should be using the FSM
               ;; to control transitions from one screen
               ;; to the next.
               (create []
                 (comment (log/info "Trying to associate the Main 3-D View:\n"
                                    (with-out-str (pprint main-view-3d))
                                    "\nand the HUD:\n"
                                    (with-out-str (pprint hud))
                                    "\nas the screens to\n"
                                    (with-out-str (pprint this))))
                 (log/info "Associating screens w/ game")
                 (try
                   (play-clj/set-screen! this main-view-3d hud)
                   ;;(play-clj/set-screen! this hud)
                   (log/info "Screen(s) associated")
                   (catch clojure.lang.ExceptionInfo ex
                     (log/error ex "Failed to associate screens with game")
                     (raise [:screen-association-failure
                             {:reason ex}]))
                   (catch RuntimeException ex
                     (log/error ex "'Normal' runtime failure")
                     (raise [:screen-association-failure
                             {:reason ex}]))
                   (catch Exception ex
                     (log/error ex "Unexpected failure")
                     (raise [:screen-association-failure
                             {:reason ex}]))
                   (catch Throwable ex
                     (log/error ex "Totally unexpected")
                     (raise [:screen-association-failure
                             {:reason ex}]))))))]
       (do
         (play-clj/set-screen! listener main-view-3d hud)
         listener)
       (log/info "Game Listener created.")
       (assoc this listener game)))
     (catch RuntimeException ex
       ;; log/error really should print the exception details for me.
       ;; Then again, it also shouldn't just disappear from the REPL
       (println "FAIL:\nTrying to set up the actual Game and Application\n"
                (with-out-str (pprint ex))
                "\nTrying to associate a game built around\n"
                #_(comment (with-out-str graphics))
                "HUD: " (with-out-str (pprint (select-keys (:hud graphics) [:entities :screen])))
                "\nMain 3d View: " (with-out-str (pprint (select-keys (:main-view-3d graphics) [:entities :screen]))))
       (log/error ex "Setting up the Application failed\n"
                  (with-out-str (pst)))
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
  (stop
   [this]
   (into this {:listener nil
               :owner nil})))

;;; TODO: Doing this here breaks most of the point behind play-clj,
;;; quite horribly.
;;; At the very least, move it over to the same sort of directory
;;; structure to take advantage of all the cross-platform work
;;; that's happened.
(s/defrecord App [app :- Application
                  game :- MainListener
                  graphics :- Graphics
                  session :- Session]
  component/Lifecycle
  (start
   [this]
   (if-not app
     (let [title (:title session)
           position (:position session)
           width (:width position)
           height (:height position)]
       (try
         ;; N.B. At least on the Desktop, this can only happen once
         (log/info "Creating the actual Application")
         ;; TODO: At the very least, need to create something
         ;; different here depending on which platform we're
         ;; running on.
         (let [app (LwjglApplication. game title width height)]
           (raise [:not-implemented
                   {:problem "Don't want to create the Application inside the Lifecycle"
                    :solution "I'm not sure about this yet"}])
           (try
             (log/info "Main window initialized")
             (into this {:listener game
                         :owner app})
             (catch RuntimeException ex
               (log/error ex "Well, at least I made it past App setup")
               (raise [:application-failure
                       {:reason ex}]))))
         (catch java.lang.IllegalStateException ex
           (log/error ex "Illegal State:\n"
                      (.getMessage ex)))
         (catch RuntimeException ex
           (log/error ex "Problem definitely stems from trying to set up the Application\n"
                      "Game: " game
                      "\nTitle: " title
                      "\nWidth: " width
                      "\nHeight: " height
                      "\nException: " (with-out-str (pprint ex)))
           (raise [:application-failure
                   :reason ex]))
         (catch Exception ex
           (log/error ex "Unexpectedly failed to set up Application\n"
                      "Game: " game
                      "\nTitle: " title
                      "\nWidth: " width
                      "\nHeight: " height
                      "\nException: " (with-out-str (pprint ex))))
         (catch Throwable ex
           (log/error ex "Severely failed to set up Application\n"
                      "Game: " game
                      "\nTitle: " title
                      "\nWidth: " width
                      "\nHeight: " height
                      "\nException: " (with-out-str (pprint ex))))))
     )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn new-application
  []
  (map->App {}))

(defn new-listener
  []
  (map->MainListener {}))
