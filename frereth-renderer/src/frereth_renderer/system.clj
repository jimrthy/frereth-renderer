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

    ;;; After the splash screen is created, start dealing with some meat.

    ;; Start by verifying that the existing pieces are dead.
    ;; Should probably do this even before the splash screen.
    ;; It isn't like it'll take long.
    ;; Then again: it'd be better to display any errors that happen there.
    (letfn [(verify-dead [key]
              (assert (nil? @(key universe))))]
      (doseq [k [:messaging :client-socket :control-channel]]
        (verify-dead k)))
    ;; Tempting to update the splash screen now, just because.
    ;; TODO: Do something interesting. Start the colors shifting
    ;; around, or some such.
    (async/>!! visualization-channel :sterile-environment)

    (let [ctx (mq/context 1)
          socket (mq/connected-socket ctx :req (config/client-url))
          chan (async/chan)]
      (reset! (:messaging universe) ctx)
      (reset! (:client-socket universe) socket)
      (reset! (:control-channel universe) chan)
      (reset! (:visualizer universe) visualization-channel)
      ;; This is wrong...at this point, really should start feeding
      ;; messages back and forth between the channel and the 
      ;; client-socket.
      (async/>!! visualization-channel :ready-to-play)
      universe)))

(defn stop
  "Perform the side-effects to sterilize a universe"
  [universe]
  (async/>!! @(:visualizer universe) :exiting)
  (async/close! @(:control-channel universe))
  (mq/close @(:client-socket universe))
  ;; Realistically: want to take some time to allow that socket to wrap
  ;; everything up.
  (mq/terminate @(:messaging universe))
  (graphics/stop)
  (init))

