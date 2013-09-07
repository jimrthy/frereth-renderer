(ns frereth-renderer.core
  (:require [frereth-renderer.graphics :as graphics]
            [frereth-renderer.system :as sys])
  (:gen-class))

(defn fsm [world]
  (let [chan @(:control-channel universe)
        a (atom true)]
    ;; TODO: Show a splash screen
    (go (>! chan :ready)
        ;; Connected to client. Show Splash Screen Phase 2
        (let [response (<! chan)]
          (when response
            (println "Ready Player One"))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [dead-world (sys/init)
        world (sys/start dead-world)]
    (try
      (fsm)
      (finally (sys/stop world)))))
