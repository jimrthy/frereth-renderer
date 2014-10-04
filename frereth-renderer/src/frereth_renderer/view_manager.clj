(ns frereth-renderer.view-manager
  (:require [[classlojure]
             [com.stuartsierra.component :as component]
             [frereth-renderer.application :as application ]
             [ribol.core :refer (raise)]
             [schema.core :as s]
             [taoensso.timbre :as log]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defrecord ViewManager [applications configuration session-manager]
  component/Lifecycle

  (start
    [this]
    (when (seq @applications)
      (log/warn "Starting views that are active"))

    (let [home (System/getProperty "user.home")
          clojure-16 (str "file://" home "/.m2/repository/org/clojure/clojure/1.6.0/clojure-1.6.0.jar")])
    (reduce (fn [acc session]
              (let [app
                    (classlojure/eval-in
                     clojure-16
                     'application/new-application configuration session)]
                (assoc acc (:id session) app)))
            {}
            @(:sessions session-manager)))

  (stop
    [this]
    (raise :not-implemented)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn init
  [configuration]
  (map->ViewManager {:applications (atom {})
                     :configuration configuration}))
