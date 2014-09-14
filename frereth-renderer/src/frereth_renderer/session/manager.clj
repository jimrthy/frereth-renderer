(ns frereth-renderer.session.manager
  (:gen-class)
  (:require [clojure.edn :as edn]
            [com.stuartsierra.component :as component]
            [schema.core :as s]
            [schema.macros :as sm]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(sm/defrecord SessionManager [config datomic-url]
  component/Lifecycle
  (start [this]
         (when-not datomic-url
           (if-let [cfg-file (:config-file config)]
             (do
               (let [config-string (slurp cfg-file)
                     config-map (edn/read-string config-string)]
                 (assoc this :datomic-url (:datomic-url config-map))))
             ;; Cheeseball default, that probably won't work.
             ;; But I have to start somewhere.
             (assoc this :datomic-url "datomic:free//localhost:4334/FrerethRenderer"))))
  (stop [this]
        this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn init
  [params]
  (map->SessionManager (select-keys params [:config :datomic-url])))

