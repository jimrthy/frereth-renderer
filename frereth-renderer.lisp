;;;; frereth-renderer.lisp

(in-package #:frereth-renderer)

;;; "frereth-renderer" goes here. Hacks and glory await!

(defun main (&args)
  (let ((repl (reader ns)))
    (do ((cmd (read))
	 (= cmd :quit)
	 (format t "Do something with: ~A" cmd)))))
