;;;; frereth-renderer.lisp

(in-package #:frereth-renderer)

(defun input (&optional prompt)
  (format t "~A"
	  (if prompt
	      prompt
	      "=> "))
  (read))


(defun main (&optional args)
  (declare (ignore args))
  ;; Umm....what was I thinking?
  #| (let ((repl (reader ns)))) |#
  
  (do ((cmd (input) (input)))
      ((equal cmd :quit) t)
      (format t "Do something with: ~A" cmd)))
