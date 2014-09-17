(ns frereth-renderer.util
  (:require [clojure.pprint :refer (pprint)]))

;;;; Collection of utility functions that I'm tired of
;;;; typing over and over.

(defn pretty
  [o]
  (with-out-str (pprint o)))

;; TODO: Write a macro that encompasses my
;; try/RuntimeException/Exception/Throwable
;; with embedded basically identical raise
;; calls.
