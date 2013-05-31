;;;; frereth-renderer.lisp

(in-package #:frereth-renderer)

(defun input (&optional prompt)
  (format t "~A"
	  (if prompt
	      prompt
	      "=> "))
  (read))

(defvar *client* (build-repl-y-client *client-port*))

(defun frereth (form)
  "Basic wrapper over the clojure interface. Send [a] form over nrepl.
Obviously horribly unsafe."
  (send-to-repl-y-client *client* form))

(defun main (&optional args)
  (declare (ignore args))
  (let ((client (build-repl-y-client *client-port*)))
    (do ((cmd (input) (input)))
	((equal cmd :quit) t)
      ;; Go ahead and live dangerously
      (print (eval cmd) t))))
