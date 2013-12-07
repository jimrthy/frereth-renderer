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
                        ;; Output to Client
                        :user-input (async/chan)
                        ;; Input from Client
                        :command (async/chan)
                        ;; For notifying -main that the graphics loop is terminating
                        :terminator (async/chan)})))

(defn stop!
  [live-world]
  (if-let [local-async (:user-input live-world)]
    (async/close! local-async)
    (warn "No local input channel...what happened?"))
  (if-let [command-from-client (:command live-world)]
    (async/close! command-from-client)
    (warn "Missing command channel from client"))
  (let [ctx (:context live-world)
        socket (:socket live-world)]
    ;; FIXME: Realistically, both these situations should throw an exception
    (if socket
      (mq/close! socket)
      (error "Missing communications socket"))
    (if ctx
      (mq/terminate! ctx)
      (error "Missing communications context")))
  live-world)
