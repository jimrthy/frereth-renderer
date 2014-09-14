(ns frereth-renderer.persist.query
  (:require [datomic.api :as d]
            [frereth-renderer.geometry :as geometry]
            [frereth-renderer.persist.core :as persist]
            [ribol.core :refer (raise)]
            [schema 
             [core :as s]
             [macros :as sm]])
  (:import [frereth_renderer.persist.core Database]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers

(defn load-previous-session-query
  []
  '{:find [?session]
    :in [$ ?id]
    :where [[?session :id ?id]]})

(defn calculate-session-changes
  [{:keys [title1 position1] :as old}
   {:keys [title2 position2] :as fresh}]
  (let [preliminary {:name [title1 title2]
                     :screen/left [(:left position1) (:left position2)]
                     :screen/top [(:top position1) (:top position2)]
                     :screen/width [(:width position1) (:width position2)]
                     :screen/height [(:height position1) (:height position2)]}]
    (reduce (fn [acc [k [_ altered]]]
              (assoc acc k altered))
            (filter (fn [[k v]]
                      (not= (first v) (second v)))
                    preliminary))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(sm/defn load-previous-session
  [db :- Database
   id :- s/Uuid]
  (let [q (load-previous-session-query)
        pk (first (d/q q db id))
        entity (d/entity pk)]
    {:pk pk
     :title (:name entity)
     :position {:left (:screen/left entity)
                :top (:screen/top entity)
                :width (:screen/width entity)
                :height (:screen/height entity)}}))

(sm/defn do-update-session-settings
  [db
   {:keys [title :- s/Str
           position :- geometry/rect
           id :- s/Uuid]}]
  (let [initial (load-previous-session db id)
        delta (calculate-session-changes initial
                                         {:title title :position position})]
    (when (seq delta)
      (let [txn (reduce-kv
                 (fn [acc k v]
                   (assoc acc k v))
                 {:db/id (:pk initial)}
                 delta)
            result (d/transact txn)]
        @result))))
