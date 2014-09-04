(ns frereth-renderer.core
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [frereth-renderer.graphics :as graphics]
            [frereth-renderer.system :as sys]
            [taoensso.timbre :as timbre
             :refer [trace debug info warn error fatal spy with-log-level]])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [dead-world (sys/init {})
        world (component/start dead-world)]
    (try
      ;; Wait for this to exit.
      ;; This approach seems horribly wrong.
      ;; Honestly, the client should tell it to exit when it sends a
      ;; close message.
      ;; Except that it needs to be able to shut down even (or maybe
      ;; especially) if the client goes away.
      ;; Probably shouldn't be running it like this in the first
      ;; place. Except...what are the better options?
      ;; Currently, this thread should be a future. So should be
      ;; able to just deref it to get everything to pause here.
      (info "Waiting on the front-end thread to exit. Value in the atom:\n"
            #_(:front-end world))
      (comment (.join @(:front-end world)))
      (comment (deref @(:front-end world)))
      (let [terminal-channel (-> world :messaging deref :terminator)]
        (async/<!! terminal-channel))
      (info "Graphics thread has exited")

      ;; Then clean up
      ;; I'm getting here almost instantly. Meaning that front-end
      ;; apparently isn't getting set to anything that seems reasonable.
      (finally (component/stop world)))))
