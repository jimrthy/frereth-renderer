(ns frereth-renderer.core
  (:require [frereth-renderer.graphics :as graphics]
            [frereth-renderer.system :as sys]
            [zguide.zhelpers :as mq])
  (:gen-class))

(defn fsm
  "OK, so it isn't exactly an impressive state machine"
  [universe]
  (println "Initializing State Machine")
  (let [sock @(:client-socket universe)]
    ;; TODO: Show a splash screen
    (mq/send sock "ready to draw" 0)
    (println "Waiting for Response from Client")
    (let [response (mq/recv sock)]
      ;; TODO: Move on to do something interesting.
      (println (str response)))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [dead-world (sys/init)
        world (sys/start dead-world)]
    (try
      (fsm world)
      (finally (sys/stop world)))))
