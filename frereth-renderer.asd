;;;; frereth-renderer.asd

(asdf:defsystem #:frereth-renderer
  :serial t
  :description "The UI layer for frereth"
  :author "jamesgatannah@gmail.com"
  :license "llgpl"
  :depends-on (#:frenv
               #:alexandria)
  :components ((:file "package")
               (:file "frereth-renderer")))

