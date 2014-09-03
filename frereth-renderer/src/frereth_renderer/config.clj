(ns frereth-renderer.config)

(defn client-url []
  "tcp://localhost:7840")

(defn defaults
  []
  (:client-url (client-url)))
