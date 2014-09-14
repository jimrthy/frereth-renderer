(ns frereth-renderer.geometry
  (:require [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def Rectangle {:left s/Int
                :top s/Int
                :width s/Int
                :height s/Int})
