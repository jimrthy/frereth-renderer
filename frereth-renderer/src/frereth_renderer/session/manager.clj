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
  "Have to start somewhere.

But this is still missing a major point.
This is looking at a Session as a window.
Each Window can have multiple Views, with each
View being a real 'window' into a Server.

The terminology's wrong, which means the implementation
is also wrong.

Or something along those lines.

This doesn't mean what my web-oriented brain insists on
thinking it means."
  [persistence]
  ;; TODO: Have the database tell us what to do.
  ;; If it's a "real" restore, do that instead.
  [{:title "Frereth Left"
    :position {:left 0
               :top 0
               :width 1200
               :height 900}
    :id (java.util.UUID/randomUUID)}
   {:title "Frereth Right"
    :position {:left 1200
               :top 0
               :width 1200
               :height 900}
    :id (java.util.UUID/randomUUID)}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn init
  [params]
  (map->SessionManager (-> (select-keys params [:config :datomic-url])
                           (assoc :sessions (atom [])))))

