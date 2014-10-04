(ns frereth-renderer.application
  (:require [clojure.pprint :refer (pprint)]
            [clojure.repl :refer (pst)]
            [com.stuartsierra.component :as component]
            [play-clj.core :as play-clj]
            [play-clj.ui :as play-ui]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:import [com.badlogic.gdx Application Game]
           [com.badlogic.gdx.backends.lwjgl LwjglApplication]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(declare post splash)
(s/defrecord MainListener [listener :- Game]
  component/Lifecycle
  (start
   [this]
   (if listener
     (play-clj/set-screen! listener splash)
     (raise [:not-implemented
             {:reason "Need to try classloader fanciness"}])))

  (stop
   [this]
   (if listener
     (play-clj/set-screen! listener post)
     (raise [:not-implemented
             {:reason "How did we get here?"}]))))

(s/defrecord App [app :- Application listener :- Game]
  component/Lifecycle
  (start
   [this]
   (component/start listener)
   this)

  (stop
   [this]
   (component/stop listener)
   this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Splash Screens
;;; TODO: Refactor these out of graphics, though that's where
;;; they really seem to belong

(play-clj/defscreen pre-init
  :on-show
  (fn [screen entities]
    (play-clj/update! screen :camera (play-clj/orthographic) :renderer (play-clj/stage))
    (assoc (play-ui/label "Before" (play-clj/color :red))
      :id :fps
      :x 5))

  :on-render
  (fn [screen entities]
    (->> (for [entity entities]
           (case (:id entity)
             :fps (doto entity (play-ui/label! :set-text (str "Before: " (play-clj/game :fps))))
             entity))
         (play-clj/render! screen)))

  :on-resize
  (fn [screen entities]
    (play-clj/height! screen 300))  )

;;; Initial Splash screen just so we have a starting point
(play-clj/defscreen splash
  :on-show
  (fn [screen entities]
    (play-clj/update! screen :camera (play-clj/orthographic) :renderer (play-clj/stage))
    (assoc (play-ui/label "0" (play-clj/color :white))
      :id :fps
      :x 5))

  :on-render
  (fn [screen entities]
    (->> (for [entity entities]
           (case (:id entity)
             :fps (doto entity (play-ui/label! :set-text (str (play-clj/game :fps))))
             entity))
         (play-clj/render! screen)))

  :on-resize
  (fn [screen entities]
    (play-clj/height! screen 300)))

(play-clj/defscreen post
  :on-show
  (fn [screen entities]
    (play-clj/update! screen :camera (play-clj/orthographic) :renderer (play-clj/stage))
    (assoc (play-ui/label "After" (play-clj/color :olive))
      :id :fps
      :x 5))

  :on-render
  (fn [screen entities]
    (->> (for [entity entities]
           (case (:id entity)
             :fps (doto entity (play-ui/label! :set-text (str "After: " (play-clj/game :fps))))
             entity))
         (play-clj/render! screen)))

  :on-resize
  (fn [screen entities]
    (play-clj/height! screen 300))  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(defn game-create-handler
  [this]
  (log/info "Associating initial screen w/ game")
  (try
    (play-clj/set-screen! this pre-init)
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
              {:reason ex}]))))

(s/defn new-listener :- MainListener
  []
  (log/info "Initializing Main Window")
  (try
    (let [game
          (proxy [Game] []
            ;; This is a convenient way to handle things.
            ;; Except that it feels wrong.
            ;; It seems like I should be using the FSM
            ;; to control transitions from one screen
            ;; to the next.
            (create []
              (game-create-handler this)))]
      (log/info "Game Listener created.")
      {:listener game})
    (catch RuntimeException ex
      ;; log/error really should print the exception details for me.
      ;; Then again, it also shouldn't just disappear from the REPL
      (println "FAIL:\nTrying to set up the actual Game")
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn new-application :- App
  [configuration]
  (let [game (new-listener)
        platform (:platform configuration)
        ;; TODO: Load these next from configuration
        ;; Actually, get a default database from
        ;; there, and load these from it.
        title "Frereth"
        width 1200
        height 900]
    (try
      (let [;; It seems ridiculous that I can't break
            ;; these cases down more thoroughly.
            ;; It seems to be a java interop limitation.
            ;; Q: What about something like (. new (cond ...))
            ;; A: That can't resolve symbol new
            ;; Oh well. It's not like this is all that
            ;; much extra boilerplate.
            application (condp = platform
                          :desktop (new LwjglApplication
                                        game title width height)
                          (raise [:not-implemented
                                  :platform (platform)]))]
        {:app application
         :listener game})
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
                   "\nException: " (with-out-str (pprint ex)))
        (raise [:application-failure
                :reason ex]))
      (catch Throwable ex
        (log/error ex "Severely failed to set up Application\n"
                   "Game: " game
                   "\nTitle: " title
                   "\nWidth: " width
                   "\nHeight: " height
                   "\nException: " (with-out-str (pprint ex)))
        (raise [:application-failure
                :reason ex])))))
