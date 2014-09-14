(ns frereth-renderer.config
  (:require [clojure.java.io :as io]
            [ribol.core :as ribol :refer (raise)]))

(defn client-url []
  {:protocol "inproc"
   :address "renderer<->client"
   :port nil})

(defn find-configuration-file
  []
  ;; This should really go under $XDG_CONFIG_HOME instead.
  ;; That isn't defined on my system, and it seems even less
  ;; likely to be defined under Windows. So just go with its
  ;; default
  (let [base (if-let [xdg-cfg (System/getenv "XDG_CONFIG_HOME")]
               xdg-cfg
               (str (System/getProperty "user.home") "/.config/"))]
    (let [preferred (io/file base "frereth" "renderer.edn")]
      (if (.exists preferred)
        preferred
        (raise [:missing
                {:todo "Search broader system paths"
                 :alt "If there is nothing, get the basic 
config from the user interactively and
save it manually."}])))))

(defn defaults
  []
  {:client-url (client-url)
   :config-file (find-configuration-file)})
