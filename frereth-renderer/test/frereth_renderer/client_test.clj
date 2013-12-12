(ns frereth-renderer.client-test
  (:require [[frereth-renderer.graphics :as g]
             [clojure.pprint :refer [pprint]]])
  (:use midje.sweet))

(facts "Render listens for an FSM.
I just want to make one up.
This part is ugly, and we know it."
       (let [initial (g/init-fsm)]
         
         (let [comms (g/begin-communications initial )]
           (println "I'm pretty sure things start failing here"))))
