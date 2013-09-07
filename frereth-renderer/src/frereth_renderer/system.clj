(ns frereth-system
  (:require [clojure.core.async :as async]
            [frereth-renderer.graphics :as graphics]
            [zguide.zhelpers :as mq])
  (:gen-class))

(defn init
  "Generate a dead system"
  []
  {:messaging (atom nil)
   :client-socket (atom nil)
   :control-channel (atom nil)})

(defn start
  "Perform the side-effects to bring a dead system to life"
  [universe]
  (letfn (verify-dead [key]
           (assert (nil? @( universe))))
    (doseq [k [:messaging :client-socket :control-channel]]
      (verify-dead k)))

  (let [ctx (mq/context 1)
        socket (mq/connected-socket ctx :req (config/client-url))
        chan (async/chan)]
    (swap! (:messaging universe) (atom ctx))
    (swap! (:client-socket universe) (atom socket))
    (swap! (:control-channel universe (atom chan)))
    universe))

(defn stop
  "Perform the side-effects to sterilize a universe"
  [universe]
  (async/close @(:control-channel universe))
  (mq/stop @(:client-socket universe))
  ;; Realistically: want to take some time to allow that socket to wrap
  ;; everything up.
  (mq/terminate @(:messaging universe))
  (init))

