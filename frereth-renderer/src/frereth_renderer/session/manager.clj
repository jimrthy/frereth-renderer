(ns frereth-renderer.session.manager
  (:gen-class)
  (:require [clojure.edn :as edn]
            [com.stuartsierra.component :as component]
            [frereth-renderer.session.core :as session]
            [schema.core :as s]
            [schema.macros :as sm]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(declare session-restore)
(sm/defrecord SessionManager [persistence sessions]
  component/Lifecycle
  (start
   [this]
   (let [ss (session-restore persistence)]
     (into this {:sessions (reset! sessions ss)})))

  (stop
   [this]
   (into this {:sessions (reset! sessions [])})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(defn session-restore
  "Have to start somewhere"
  [persistence]
  ;; TODO: Have the database tell us what to do.
  ;; If it's a "real" restore, do that instead.
  [(component/start (session/init {:width 800 :title "Frereth Left"}))
   (component/start (session/init {:left 800 :width 800 :title "Frereth Right"}))])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn init
  [params]
  (map->SessionManager (-> (select-keys params [:config :datomic-url])
                           (assoc :sessions (atom [])))))

