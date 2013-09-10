(ns frereth-renderer.system
  (:require [clojure.core.async :as async]
            [frereth-renderer.config :as config]
            [frereth-renderer.graphics :as graphics]
            [zguide.zhelpers :as mq])
  (:gen-class))

(defn init
  "Generate a dead system"
  []
  {:messaging (atom nil)
   :client-socket (atom nil)
   ;; TODO: Create a "Top Level" window?
   ;; Note: if I decide to go with LWJGL, I should be able
   ;; to have multiple windows by using multiple classloaders
   ;; and loading the library separately into each.
   ;; Seems like overkill...but also an extremely good idea.
   :control-channel (atom nil)})

(defn start
  "Perform the side-effects to bring a dead system to life"
  [universe]
  (letfn [(verify-dead [key]
            (assert (nil? @(key universe))))]
    (doseq [k [:messaging :client-socket :control-channel]]
      (verify-dead k)))

  (let [ctx (mq/context 1)
        socket (mq/connected-socket ctx :req (config/client-url))
        chan (async/chan)]
    (reset! (:messaging universe) ctx)
    (reset! (:client-socket universe) socket)
    (reset! (:control-channel universe) chan)
    universe))

(defn stop
  "Perform the side-effects to sterilize a universe"
  [universe]
  (async/close! @(:control-channel universe))
  (mq/close @(:client-socket universe))
  ;; Realistically: want to take some time to allow that socket to wrap
  ;; everything up.
  (mq/terminate @(:messaging universe))
  (init))

