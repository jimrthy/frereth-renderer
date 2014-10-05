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

(s/defrecord Database [connection url]
  component/Lifecycle
  (start
   [this]
   (if url
     (do
       (when (d/create-database url)
         (log/info "Creating new database at " url)
         (let [db (d/connect url)
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
       (try
         (into this {:connection (d/connect url)})
         (catch NullPointerException ex
           (log/error ex "Trying to connect to " url)
           (raise [:db-connection
                   {:reason ex
                    :url url}]))))
     (raise [:missing-url])))
           
  (stop
   [this]
   (assoc this :connection nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(s/defn new-database :- Database
  [configuration]
  (log/debug "Setting up database based on:\n"
             (with-out-str (pprint configuration)))
  (map->Database {:url (:database-url configuration)}))
