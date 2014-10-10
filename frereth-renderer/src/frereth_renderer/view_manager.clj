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
      (log/warn "Starting views that are active")
      (raise [:not-implemented
              {:why "What should happen?"}])))

  (stop
    [this]
    (raise :not-implemented)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn init
  [configuration]
  (map->ViewManager {:applications (atom {})
                     :configuration configuration}))
