(ns frereth-renderer.view-manager
  (require [[classlojure.core :as classlojure]]))

;;; N.B. This is really intended for starting with a totall different version of clojure.
;;; It loads RT and then invokes main on whichever form you provide

;;; But that should really be perfect for my needs. It creates a new classloader from the
;;; supplied clojure version (Q: how do I get the current one? A: Worst-case, parse
;;; (system/getProperty "java.class.path")). *And* it leaves my basic classpath
;;; unchanged. So I should be able to just set this up, create a new Application, and return
;;; that.
;;; Heck. I might even be able to just discard the temp classloader and let it get garbage
;;; collected. Though that seems pretty pie-in-the-sky

;; TODO: Don't forget to double-check Pomegranate
(let [play-jar-file "file:/home/james/.m2/repository/org/clojure/clojure/1.6.0/clojure-1.6.0.jar"
      cl (classlojure/classlojure play-jar-file)]
  (classlojure/eval-in cl '*clojure-version*))


