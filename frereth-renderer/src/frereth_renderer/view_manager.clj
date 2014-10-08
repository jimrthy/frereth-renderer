(ns frereth-renderer.view-manager
  (:require [classlojure.core :as classlojure]
            [clojure.pprint :refer (pprint)]
            [com.stuartsierra.component :as component]
            
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

;; Q: Why is this here?
(comment (defn build-application
           [configuration session]
           (require '[frereth-renderer.application])
           (frereth-renderer.application/new-application configuration session)))

(defrecord ViewManager [applications configuration session-manager]
  component/Lifecycle

  (start
    [this]
    (log/debug "Starting the View Manager from sessions:\n"
               (with-out-str (pprint session-manager)))
    (when (seq @applications)
      (log/warn "Starting views that are active"))

    (let [sessions-atom (:sessions session-manager)
          sessions @sessions-atom
          home (System/getProperty "user.home")
          ;; TODO: Fetch these from a URL if they aren't already present.
          ;; They need to go into some well-known subdirectory.
          ;; Which makes running this from a .jar *very* problematic.
          ;; The classlojure code (unit tests?) has a good example.
          ;; TODO: just copy this from the existing CLASSPATH instead.
          prefix (str "file://" home "/.m2/repository/")
          clojure (str prefix "org/clojure/clojure/1.6.0/clojure-1.6.0.jar")
          stu-sierra (str prefix "com/stuartsierra/")
          ss-component (str stu-sierra "component/0.2.2/component-0.2.2.jar")
          ss-dependency (str stu-sierra "dependency/0.1.1/dependency-0.1.1.jar")
          gdx-base (str prefix "com/badlogicgames/gdx/")
          libgdx (str gdx-base "gdx/1.3.1/gdx-1.3.1.jar")
          gdx-backend (str gdx-base "gdx-backend-lwjgl/1.3.1/gdx-backend-lwjgl-1.3.1.jar")
          play (str prefix "play-clj/play-clj/0.3.11/play-clj-0.3.11.jar")
          lwjgl-base (str prefix "org/lwjgl/lwjgl/")
          lwjgl (str lwjgl-base "lwjgl/2.9.1/lwjgl-2.9.1.jar")
          #_(comment lwjgl-utils (str lwjgl-base "lwjgl_util/2.9.1/swjgl_util-2.9.1.jar"))
          working "file:src/"

          result
          (reduce (fn [acc session]
                    (let [details {"platform" (name (:platform configuration))
                                   "title" (:title session)
                                   "width" (:width session)
                                   "height" (:height session)}
                          clojure-16 (classlojure/classlojure clojure
                                                              gdx-backend
                                                              libgdx
                                                              lwjgl
                                                              play
                                                              ss-component
                                                              ss-dependency
                                                              working)
                          base-line
                          (classlojure/eval-in
                           clojure-16
                           '(do
                              (println "Setting up a new Application, with classpath:\n"
                                       (System/getProperty "java.class.path")
                                       "\n")
                              (try
                                (require 'frereth-renderer.application)
                                (catch java.io.FileNotFoundException ex
                                  (println "Missing File")
                                  (let [msg "\n\nWhy can't I find this?"]
                                    (println ex)
                                    (dorun (map println (.getStackTrace ex)))
                                    (println msg)
                                    (throw ex)))
                                (catch clojure.lang.ExceptionInfo ex
                                  (println "Fail")
                                  (let [msg "\n\nWhat on Earth is going on?"]
                                    (println ex "\n" (.getStackTrace ex) msg)
                                    (throw ex))))
                              (println "ns successfully required")))
                          app
                          (classlojure/eval-in
                           clojure-16
                           'frereth-renderer.application/new-application
                           details)]
                      (assoc acc (:id session) app)))
                  {}
                  sessions)]
      (log/debug "View Manager started")))

  (stop
    [this]
    (raise :not-implemented)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn init
  [configuration]
  (map->ViewManager {:applications (atom {})
                     :configuration configuration}))
