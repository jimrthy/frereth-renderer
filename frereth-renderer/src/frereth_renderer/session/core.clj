(ns frereth-renderer.session.core
  (:require [com.stuartsierra.component :as component]
            [frereth-renderer.geometry :as geometry]
            [frereth-renderer.persist.core :as persist]
            [frereth-renderer.persist.query :as query]
            [frereth-renderer.session.manager :as manager]
            [schema.core :as s]
            [schema.macros :as sm])
  (:import [frereth-renderer.persist.core Database])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(sm/defrecord Session [persistence :- Database
                       title :- s/Str
                       position :- geometry/Rectangle
                       id :- s/Uuid
                       ;; Q: Does any of the rest of this belong in here?
                       message-coupling
                       fsm
                       update-function]
  ;; As-is, it's tempting to make this a plain-ol' map.
  ;; There are pieces that I definitely want/need to
  ;; implement here.
  ;; It might qualify as YAGNI, but I definitely want
  ;; to implement them soon.
  component/Lifecycle
  (start [this]
         (let [delta (if id
                       (query/load-previous-session persistence id)
                       ;; TODO: Don't overwrite any settings that have
                       ;; already been supplied
                       ({:title "Unknown"
                         :position {:left 0
                                    :top 0
                                    :width 800
                                    :height 600}
                         :id (persist/new-id)}))]
           (into this delta)))

  (stop [this]
        (let [id (query/do-update-session-settings persistence
                                                   (select-keys this [:title
                                                                      :position
                                                                      :id]))]
          ;; Actually, this seems like a situation where an atom would be
          ;; more appropriate. Changing one of those is safer and involves
          ;; smaller side-effects than actually writing to the file system.
          (raise [:not-implemented
                  :missing "Have the Session Manager store that ID"]))
        ;; Q: Does Components take care of this for me?
        (comment (into this {:persistence nil}))
        this))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn new-session
  "This is pretty horribly over-simplified.
But it's a start
"
  [{:keys [width height title update-function]
    :or {width 1024
         height 768
         title "Frereth"}}]
  (map->Session {:width width
                 :height height
                 :title title}))
