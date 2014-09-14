(ns frereth-renderer.logging
  (require [com.postspectacular.rotor :as rotor]
           [com.stuartsierra.component :as component]
           [taoensso.timbre :as log
            :refer (trace debug info warn error fatal spy with-log-level)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(declare pick-log-file-name)
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
     {:path (pick-log-file-name) :max-size (* 512 1024) :backlog 5})
    (log/warn "FIXME: Log to a database instead")
    this)

  (stop
    [this]
    ;; Q: How do I actually disable loggers?
    (log/warn "Stopping logging...but not really")
    this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utilities

(defn pick-log-file-name
  []
  ;; FIXME: Be smarter about this
  "/home/james/.config/frereth/logs/app.log")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn new
  []
  (->Logger))
