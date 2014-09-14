(ns frereth-renderer.persist.schema
  (:require [datomic.api :as d]
            [schema.core :as s]
            [schema.macros :as sm]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def LegitDatomicTypes
  (s/enum :db.type/keyword
          :db.type/string
          :db.type/boolean
          :db.type/long
          :db.type/bigint
          :db.type/float
          :db.type/double
          :db.type/bigdec
          :db.type/ref
          :db.type/instant
          :db.type/uuid
          :db.type/uri
          :db.type/bytes))

(def AttributeTransaction
  {:db/id s/Any  ; TODO: What class is this really?
   :db/ident s/Keyword  ; Q: Is it always?
   :db/valueType LegitDatomicTypes
   :db/cardinality (s/either :db.cardinality/one
                             :db.cardinality/many)
   :db/doc s/Str
   :db.install/_attribute :db.part/db})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Attribute definers

(defmacro define-attribute
  [attr-name
   type]
  ;; TODO: How do I get prismatic schema working inside here?
  `(defn ~attr-name
     ([~'name
       ~'doc
       ~'many]
        {:db/id (d/tempid :db.part/db)
         :db/ident ~'name
         :db/valueType ~type
         :db/cardinality (if-not ~'many
                           :db.cardinality/one
                           :db.cardinality/many)
         :db/doc ~'doc
         :db.install/_attribute :db.part/db})
     ([~'name
       ~'doc]
        (~attr-name ~'name ~'doc false))))

(define-attribute instant-attr :db.type/instant)
(define-attribute long-attr :db.type/long)
(define-attribute string-attr :db.type/string)
(define-attribute uuid-attr :db.type/uuid)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Actual attributes

(defn general
  []
  [(string-attr :name "Something humans can use to identify this entity")
   (uuid-attr :id "Way for machines to identify")])

(defn screen-details
  []
  [(long-attr :screen/left "Screen coordinate of the window's left side")
   (long-attr :screen/top "Screen coordinate of the window's top side")
   (long-attr :screen/width "Distance from left to right sides, in screen units")
   (long-attr :screen/height "Distance from top to bottom sides, in screen units")])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn define
  "Return a transaction to manipulate the database
into the state we actually need."
  []
  (concat (general)
          (screen-details)))

