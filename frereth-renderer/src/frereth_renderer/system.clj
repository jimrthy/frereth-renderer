(ns frereth-renderer.system
  (:require [clojure.core.async :as async]
            [frereth-renderer.config :as config]
            [frereth-renderer.graphics :as graphics]
            [cljeromq.core :as mq])
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
   :control-channel (atom nil)
   :visualizer (atom nil)})

(defn start
  "Perform the side-effects to bring a dead system to life"
  [universe]
  (let [visualization-channel (async/chan)]
    ;; TODO: Don't use magic numbers.
    ;; TODO: Remember window positions from last run and reset them here.
    ;; N.B.: That really means a custom classloader. Which must happen
    ;; eventually anyway. I just want to put it off as long as possible.
    (let [visual-details  {:width 1024
                           :height 768
                           :title "Frereth"
                           :controller visualization-channel}]
      ;; The display has to be created in the same thread where the
      ;; drawing happens.
      ;; This seems to totally go against the grain of lwjgl in general...
      ;;(graphics/build-display visual-details)
      (letfn [(eye-candy []
                (graphics/begin visual-details))]
        (.start (Thread. eye-candy))))

(defn stop
  "Perform the side-effects to sterilize a universe"
  [universe]
  (async/>!! @(:visualizer universe) :exiting)
  (async/close! @(:control-channel universe))
  (mq/close @(:client-socket universe))
  ;; Realistically: want to take some time to allow that socket to wrap
  ;; everything up.
  (mq/terminate @(:messaging universe))
  (graphics/stop!)
  (init))



