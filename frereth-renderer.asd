;;;; frereth-renderer.asd

(asdf:defsystem #:frereth-renderer
  :serial t
  :description "The UI layer for frereth"
  :author "jamesgatannah@gmail.com"
  :license "LGPL"
  :depends-on (#:frenv
               #:alexandria
	       #:zeromq)
  :components ((:file "package")
	       (:file "configuration")
               (:file "frereth-renderer")))

