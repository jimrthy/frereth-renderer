(defproject frereth-renderer "0.0.1-SNAPSHOT"
  :description "A renderer suitable for frereth, clojure style."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[byte-transforms "0.1.0"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.0-SNAPSHOT"]
                 [org.jeromq/jeromq "0.3.0-SNAPSHOT"]
                 [org.zeromq/cljzmq "0.1.1" :exclusions [org.zeromq/jzmq]]
                 ;; TODO: An nrepl client?
                 ;;[org.clojars.toxi/jogl "2.0.0-rc11"]
                 ;; N.B. Charles Stain has 3.0 binaries on clojars!
                 [org.lwjgl.lwjgl/lwjgl "2.9.0"]
                 ;; There's a stack-overflow answer claiming that this
                 ;; next line removes the need for specifying a native
                 ;; lib. I'm not having any luck.
                 ;;[org.lwjgl/lwjgl-native-platform "2.9.0"]
                 [org.lwjgl.lwjgl/lwjgl-platform "2.9.0" :classifier "natives-windows" :native-prefix ""]
                 [org.lwjgl.lwjgl/lwjgl_util "2.9.0"]]
  ;; Needed to get to lwjgl native libs...maybe
  :jvm-opts [~(str "-Djava.library.path=native/:"
                   (System/getProperty "java.library.path"))]
  ;; One is needed for core.async.
  ;; I think the other's for jeromq.
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"
                 "sonatype-nexus-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"}
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :dependencies  [[org.clojure/tools.namespace "0.2.3"]
                                  [org.clojure/java.classpath "0.2.0"]
                                  [org.clojure/tools.logging "0.2.6"]]}}
  :main frereth-renderer.core)
