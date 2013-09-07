(defproject frereth-renderer "0.0.1-SNAPSHOT"
  :description "A renderer suitable for frereth, clojure style."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.zeromq/jzmq "2.2.0"]
                 ;; TODO: An nrepl client?
                 [org.clojars.toxi/jogl "2.0.0-rc11"]]
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :dependencies  [[org.clojure/tools.namespace "0.2.3"]
                                  [org.clojure/java.classpath "0.2.0"]
                                  [org.clojure/tools.logging "0.2.6"]]}}
  :main frereth-renderer.core)
