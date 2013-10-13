(ns frereth-renderer.communications
  (:require [cljeromq.core :as mq]
            [clojure.core.async :as async]
            [taoensso.timbre :as timbre
             :refer [trace info debug warn error fatal spy with-log-level]])
  (:gen-class))

(defn init
  []
  (atom nil))

(defn start
  "N.B. dead-world is an atom.
TODO: formalize that using something like core.contract"
  [dead-world]
  ;; TODO: It actually might make some sort of sense to have multiple
  ;; threads involved here.
  (let [ctx (mq/context 1)
        ;; TODO: what kind of socket makes sense here?
        socket (mq/socket ctx :router)
        local-async (async/chan)]
    ;; FIXME: Do I want into, merge, or something totally different?
    ;; FIXME: Whichever. Need an async channel that uses this socket
    ;; to communicate back and forth with the graphics namespace
    ;; to actually implement the UI.
    (reset! dead-world {:context ctx
                        :socket socket
                        :local-mq (async/chan)})))

(defn stop!
  [live-world]
  (if-let [local-async (:local-mq live-world)]
    (async/close! local-async)
    (warn "No local async channel...what happened?"))
  (let [ctx (:context live-world)
        socket (:socket live-world)]
    (mq/close socket)
    (mq/terminate ctx))
  live-world)
