(ns frereth-renderer.persist.core
  (:require [clojure.pprint :refer (pprint)]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [frereth-renderer.persist.schema :as schema]
            [ribol.core :refer (raise)]
            [schema.core :as s]
            [taoensso.timbre :as log])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(s/defrecord Database [db-url]
  component/Lifecycle
  (start [this]
         (when (d/create-database db-url)
           (log/info "Creating new database at " db-url)
           (let [db (d/connect db-url)
                 txn (schema/define)]
             (try
               (let [success (d/transact db txn)]
                 (log/info "Installing schema:\n"
                           (with-out-str (pprint @success))))
               (catch clojure.lang.ExceptionInfo ex
                 (log/error ex "Failed to run schema transaction\n"
                            (with-out-str (pprint txn)))
                 (raise [:persistence-failure
                         {:reason ex}])))))
           ;; I've read that the connections are actually
           ;; quite cheap.
           ;; TODO: Check that. Although it's not like this
           ;; is an inner loop.
         (into this {:connection (d/connect db-url)}))
           
  (stop [this]
        (assoc this :connection nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn new-database :- Database
  [configuration]
  (map->Database (select-keys configuration [:db-url])))
