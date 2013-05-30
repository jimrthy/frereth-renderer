;;;; frereth-renderer.lisp

(in-package #:frereth-renderer)

(defun input (&optional prompt)
  (format t "~A"
	  (if prompt
	      prompt
	      "=> "))
  (read))

(defun build-repl-y-client (port)
  (error 'not-implemented))

(defvar *client* (build-repl-y-client *client-port*))

(defun send-to-repl-y-client (fn &rest args)
  (format t "Do something over *client*")
  ;; As soon as I get a clue what that might be...
  (error 'not-implemented))

(defun frereth (form)
  "Basic wrapper over the clojure interface. Send [a] form over nrepl.
Obviously horribly unsafe."
  (send-to-repl-y-client form))

(defun main (&optional args)
  (declare (ignore args))
  (let ((client (build-repl-y-client *client-port*)))
    (do ((cmd (input) (input)))
	((equal cmd :quit) t)
      ;; Go ahead and live dangerously
      (print (eval cmd) t))))
