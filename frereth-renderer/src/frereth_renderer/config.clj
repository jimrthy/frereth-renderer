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

(defn default-fsm
  "My plan is that this is just for testing and the REPL.
It would probably make more sense to move this into the fsm
ns and just use it as the default"
  []
  {:__init 
   {:disconnect                    [nil :disconnected]}
   :disconnected
   {:client-connect-without-server [nil :waiting-for-server]
    :client-connect-with-server    [nil :waiting-for-home-page]
    :client-connect-with-home-page [nil :main-life]}
   :waiting-for-server
   {:client-disconnect             [nil :disconnected]}
   :server-connected
   {:waiting-for-home-page         [nil :main-life]
    :client-disconnect             [nil :disconnected]}
   :main-life
   {:client-disconnect             [nil :disconnected]}})
