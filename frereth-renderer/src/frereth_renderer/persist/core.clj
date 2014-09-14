(ns frereth-renderer.persist.core
  (:require [datomic.api :as d]
            [frereth-renderer.persist.schema :as schema]
            [frereth-renderer.session.manager :as session-manager]
            [schema
             [core :as s]
             [macros :as sm]])
  (:import [frereth-renderer.session.manager SessionManager]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(sm/defrecord Database [connection
                        session-manager :- SessionManager]
  component/Lifecycle
  (start [this]
         (let [uri (:datomic-url session-manager)]
           (when (d/create-database uri)
             (log/info "Creating new database")
             (let [success (d/transact (schema/define))]
               (log/info "Installing schema:\n"
                         (with-out-str (pprint @success)))))
           (into this {:connection (d/connect uri)})))
  (stop [this]
        (assoc this :connection nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn new-id []
  (d/squuid))

(defn new-database
  []
  (map->Database {}))
