(ns frereth-renderer.persist.core
  (:require [clojure.pprint :refer (pprint)]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [frereth-renderer.persist.schema :as schema]
            [frereth-renderer.session.manager :as session-manager]
            [ribol.core :refer (raise)]
            [schema
             [core :as s]
             [macros :as sm]]
            [taoensso.timbre :as log])
  (:import [frereth_renderer.session.manager SessionManager])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(sm/defrecord Database [connection
                        session-manager :- SessionManager]
  component/Lifecycle
  (start [this]
         (let [uri (:datomic-url session-manager)]
           (when (d/create-database uri)
             (log/info "Creating new database at " uri)
             (let [txn (schema/define)]
               (try
                 (let [success (d/transact txn)]
                   (log/info "Installing schema:\n"
                             (with-out-str (pprint @success))))
                 (catch clojure.lang.ExceptionInfo ex
                   (log/error ex "Failed to run schema transaction\n"
                              (with-out-str (pprint txn)))
                   (raise [:persistence-failure
                           {:reason ex}])))))
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
