;;;; package.lisp

(defpackage #:frereth-renderer-configuration
  (:use #:cl)
  (:export *client-port*))

(defpackage #:frereth-renderer
  (:use #:cl)
  (:export #:main)
  (:import-from #:frereth-renderer-configuration :*client-port*)
  (:import-from #:frereth-communicator #:build-repl-y-client #:send-to-repl-y-client))


