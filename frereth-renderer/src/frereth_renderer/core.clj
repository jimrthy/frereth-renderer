(ns frereth-renderer.core
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [frereth-renderer.graphics :as graphics]
            [frereth-renderer.system :as sys]
            [ribol.core :refer (raise)]
            [taoensso.timbre :as log])
  (:gen-class))

(defn -main
  "Theoretically, this is the entry point where everything interesting should start."
  [& args]
  (let [dead-world (sys/build {})
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
      (log/info "Waiting on the front-end thread to exit. Value in the atom:\n"
            #_(:front-end world))
      (comment (.join @(:front-end world)))
      ;; N.B. There *is* a :terminator channel, inside (:channels world)
      ;; TODO: Go back to reading from it instead.
      (comment (deref @(:front-end world))
               (raise [:not-implemented
                       {:message "*This* is what that terminator channel is for"}])
               (let [terminal-channel (-> world :messaging deref :terminator)]
                 (async/<!! terminal-channel)))
      @(:done world)  ; This seems at least vaguely reasonable
      (log/info "Graphics thread has exited")

      ;; Then clean up
      ;; I'm getting here almost instantly. Meaning that front-end
      ;; apparently isn't getting set to anything that seems reasonable.
      (finally
        (log/info "Exiting")
        (component/stop world)
        (shutdown-agents)
        (log/info "Exited")))))
