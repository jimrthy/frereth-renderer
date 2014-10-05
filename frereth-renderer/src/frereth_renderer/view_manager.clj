(ns frereth-renderer.view-manager
  (:require [classlojure.core :as classlojure]
            [clojure.pprint :refer (pprint)]
            [com.stuartsierra.component :as component]
            
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defn build-application
  [configuration session]
  (require '[frereth-renderer.application])
  (frereth-renderer.application/new-application configuration session))

(defrecord ViewManager [applications configuration session-manager]
  component/Lifecycle

  (start
    [this]
    (log/debug "Starting the View Manager from sessions:\n"
               (with-out-str (pprint session-manager)))
    (when (seq @applications)
      (log/warn "Starting views that are active"))

    (let [home (System/getProperty "user.home")
          ;; TODO: Fetch this from a URL if it isn't already present.
          ;; It needs to go into some well-known subdirectory.
          ;; Which makes running this from a .jar *very* problematic.
          ;; The classlojure code (unit tests?) has a good example.
          jar (str "file://" home "/.m2/repository/org/clojure/clojure/1.6.0/clojure-1.6.0.jar")
          clojure-16 (classlojure/classlojure jar)
          result
          (reduce (fn [acc session]
                    (let [app
                          (classlojure/eval-in
                           clojure-16
                           '(fn [configuration session]
                              (println "Setting up a new Application, with classpath:\n"
                                       (System/getProperty "java.class.path"))
                              (require '[frereth-renderer.application])
                              (comment 
                                       (println "ns successfully required")
                                       (frereth-renderer.application/new-application configuration session))
                              (throw (RuntimeException. "What gives?")))
                           configuration session)]
                      (assoc acc (:id session) app)))
                  {}
                  @(:sessions session-manager))]
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
