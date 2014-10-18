(ns frereth-renderer.application
  (:require [clojure.pprint :refer (pprint)]
            [clojure.repl :refer (pst)]
            [com.stuartsierra.component :as component]
            [play-clj.core :as play-clj]
            [play-clj.ui :as play-ui]
            ;;[ribol.core :refer (raise)]
            ;;[schema.core :as s]
            ;;[taoensso.timbre :as log]
            )
  (:import [com.badlogic.gdx Application Game]
           [com.badlogic.gdx.backends.lwjgl LwjglApplication]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(declare post splash)
(defrecord MainListener [^Game listener]
  component/Lifecycle
  (start
   [this]
   (if listener
     (play-clj/set-screen! listener splash)
     (throw (ex-info "Need to try classloader fanciness"
                     {:not-implemented true}))))

  (stop
   [this]
   (if listener
     (play-clj/set-screen! listener post)
     (throw "How did we get here?"
            {:not-implemented true}))))

(defrecord App [^Application app ^Game listener]
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

(defn log
  [severity & objs]
  (let [home (System/getProperty "user.home")
        log-file (str home "/.config/frereth/logs/renderer-application.log")
        ;; TODO: Add a time stamp. And expand objs
        msg (str severity "\t" objs "\n")]
    (spit log-file msg :append true)))

(defn info
  [& objs]
  (log :info objs))

(defn error
  [& objs]
  (log :error objs))

(defn game-create-handler
  [this]
  (info "Associating initial screen w/ game")
  (try
    (play-clj/set-screen! this pre-init)
    ;;(play-clj/set-screen! this hud)
    (info "Screen(s) associated")
    (catch clojure.lang.ExceptionInfo ex
      (throw (ex-info "Failed to associate screens with game"
                      {:screen-association-failure true}
                      ex)))
    (catch RuntimeException ex
      (throw (ex-info "'Normal' runtime failure"
                      {:screen-association-failure true}
                      ex)))
    (catch Exception ex
      (throw (ex-info "Unexpected failure"
                      {:screen-association-failure true}
                      ex)))
    (catch Throwable ex
      (throw (ex-info "Totally unexpected"
                      {:screen-association-failure true}
                      ex)))))

(defn new-listener
  ^MainListener
  []
  (info "Initializing Main Window")
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
      (info "Game Listener created.")
      game)
    (catch RuntimeException ex
      ;; log/error really should print the exception details for me.
      ;; Then again, it also shouldn't just disappear from the REPL
      (error "FAIL:\nTrying to set up the actual Game")
      (throw (ex-info "Setting up the Application failed"
                      {:app/runtime-initialization true}
                      ex)))
    (catch Exception ex
      (throw (ex-info "Unexpected error setting up Application"
                      {:app/base-initialization true}
                      ex)))
    (catch Throwable ex
      (throw (ex-info "*Really* unexpected error setting up Application"
                      {:app/thrown-initialization true}
                      ex)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn new ^App
  [configuration]
  (info "Building a new App")

  ;; Extracting the keys from platform is failing.
  ;; Conjecture:
  ;; Its keys were set up under a different classloader.
  ;; => they don't match keywords in *this* classloader.
  ;; This seems like a sticky wicket.
  ;; First plan that springs to mind: pass in all the
  ;; parameters I need individually rather than a pair of maps.
  ;; Second plan: turn the keys into strings before passing them.
  (if-let [platform (:platform configuration)]
    (let [game (new-listener)
          window (:window configuration)
          title (:title window)
          width (or (:width window) 1440)
          height (or (:height window) 1080)]
      (try
        (let [;; It seems ridiculous that I can't break
              ;; these cases down more thoroughly.
              ;; It seems to be a java interop limitation.
              ;; Q: What about something like (. new (cond ...))
              ;; A: That can't resolve symbol new
              ;; Oh well. It's not like this is all that
              ;; much extra boilerplate.
              application (condp = platform
                            ;; TODO: LwjglCanvas is specifically designed
                            ;; to be a component embedded inside a Swing app
                            ;; For that matter, the LwjglApplication takes a
                            ;; java.awt.Canvas as a parameter...which seems like
                            ;; at least a hint that it might run well embedded
                            ;; For that matter, the JglfwApplication is pretty
                            ;; interesting as well.
                            ;; Maybe a lot more interesting.
                            :desktop (new LwjglApplication
                                          game title width height)
                            (throw (ex-info "Not Implemented"
                                            {:platform platform
                                             :configuration configuration})))]
          {:app application
           :listener game})
        (catch java.lang.IllegalArgumentException ex
          (let [msg (str (.getStackTrace ex)
                         "\nListener: " game
                         "\ntitle: " title " width: " width " height: " height)]
            (error msg)
            (throw (ex-info "See logs"
                            {:application-failure true
                             :configuration configuration}
                            ex))))
        (catch java.lang.IllegalStateException ex
          (throw (ex-info "Illegal State"
                          {}
                          ex)))
        (catch NullPointerException ex
          (let [msg (str "NPE:\n" (dorun (map #(.getString %) (.getStackTrace ex)))
                         "\nListener: " game
                         "\ntitle: " title " width: " width " height: " height)]
            (error msg)
            (throw (ex-info "See logs"
                            {:application-failure true
                             :configuration configuration}
                            ex))))
        (catch RuntimeException ex
          (error "Problem definitely stems from trying to set up the Application\n"
                 "Game: " game
                 "\nTitle: " title
                 "\nWidth: " width
                 "\nHeight: " height
                 "\nConfiguration: " configuration
                 "\nException: " (with-out-str (pprint ex)))
          (throw (ex-info "See logs"
                          {:application-failure true
                           :configuration configuration}
                          ex)))
        (catch Exception ex
          (error "Unexpectedly failed to set up Application\n"
                 "Game: " game
                 "\nTitle: " title
                 "\nWidth: " width
                 "\nHeight: " height
                 "\nException: " (with-out-str (pprint ex)))
          (throw (ex-info "See logs"
                          {:application-failure true}
                          ex)))
        (catch Throwable ex
          (error "Severely failed to set up Application\n"
                 "Game: " game
                 "\nTitle: " title
                 "\nWidth: " width
                 "\nHeight: " height
                 "\nException: " (with-out-str (pprint ex)))
          (throw (ex-info "See logs"
                          {:application-failure true}
                          ex)))))
    (let [message (str "Missing platform in\n"
                       (with-out-str (pprint configuration))
                       "\nwhich is a:\n"
                       (class configuration)
                       "\nwith the keys:\n"
                       (with-out-str (pprint (keys configuration))))]
      (error message)
      (throw (ex-info message
                      {:configuration configuration
                       :missing-platform true})))))
