(ns frereth-renderer.logging
  (require [com.postspectacular.rotor :as rotor]
           [com.stuartsierra.component :as component]
           [taoensso.timbre :as log
            :refer (trace debug info warn error fatal spy with-log-level)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(defrecord Logger []
  component/Lifecycle

  (start
    [this]
    (log/set-config!
     [:appenders :rotor]
     {:doc "Writes to (:path (:rotor :shared-appender-config)) file and creates optional backups"
      :min-level :trace
      :enabled? true
      :async? false
      :max-message-per-msecs nil
      :fn rotor/append})
    (log/set-config!
     [:shared-appender-config :rotor]
     {:path "logs/app.log" :max-size (* 512 1024) :backlog 5})
    (log/warn "FIXME: Log to a database instead")
    this)

  (stop
    [this]
    this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn new
  []
  (->Logger))
