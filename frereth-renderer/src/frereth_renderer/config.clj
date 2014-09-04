(ns frereth-renderer.config)

(defn client-url []
  {:protocol "inproc"
   :address "renderer<->client"
   :port nil})

(defn defaults
  []
  (:client-url (client-url)))
