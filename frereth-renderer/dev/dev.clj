(ns dev
  (:require [clojure.java.io :as io]
            [clojure.inspector :as i]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [com.stuartsierra.component :as component]
            ;;[datomic-schema-grapher.core :refer (graph-datomic)]
            ;;[dynalint.lint :as dyn]
            [frereth-renderer.communications :as comm]
            [frereth-renderer.config :as cfg]
            [frereth-renderer.graphics :as graphics]
            [frereth-renderer.system :as system]
            [ribol.core :refer (raise)]))

(def system nil)

(defn init
  "Constructs the current development system."
  []
  (set! *print-length* 50)
  (comment (dyn/lint))

  ;; TODO: Put these in the database
  (let [fsm-descr (cfg/default-fsm)]
    (alter-var-root #'system
                    (constantly (system/build {:fsm-description fsm-descr
                                               :initial-state :disconnected
                                               :platform :desktop})))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system component/start))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system
    (fn [s] (when s (component/stop s)))))

(defn go-go
  "Initializes the current development system and starts it running.
Can't just call this go: that conflicts with a macro from core.async."
  []
  (println "Initializing system")
  (init)
  (println "Restarting system")
  (start))

(defn reset []
  (println "Stopping")
  (stop)
  (println "Refreshing namespaces")
  ;; This pulls up a window (in the current thread) which leaves
  ;; emacs unusable. Get it at least not dying instantly from lein run,
  ;; then make this play nicely with the main window in a background
  ;; thread.
  ;; Which doesn't really work at all on a Mac: more impetus than
  ;; ever to get a REPL working there internally.
  ;; But I don't need it yet.
  (comment (raise :currently-broken))
  (try
    (refresh :after 'dev/go-go)
    (catch clojure.lang.ExceptionInfo ex
      (pprint ex)
      (println "Refresh failed"))))
