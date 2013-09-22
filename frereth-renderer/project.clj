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
                 ;;[org.lwjgl.lwjgl/lwjgl "2.9.0"]
                 ;;[org.lwjgl.lwjgl/lwjgl_util "2.9.0"]
                 ;; For now, this is a local git-dep.
                 ;;[penumbra "0.6.6-SNAPSHOT"]
                 ;; FIXME: Is this more appropriate here or in frereth-client?
                 [simplecs "0.1.0"]]
  :git-dependencies [["git@github.com:jimrthy/penumbra.git"]
                     ["git@github.com:jimrthy/cljeromq.git"]]
  :source-paths ["src" 
                 ".lein-git-deps/penumbra/src/"
                 ".lein-git-deps/cljeromq/src/"]
  ;; Needed to get to lwjgl native libs...is this still true w/ penumbra?
  :jvm-opts [~(str "-Djava.library.path=native/:"
                   (System/getProperty "java.library.path"))]
  :plugins [[lein-git-deps "0.0.1-SNAPSHOT"]]
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :dependencies  [[org.clojure/tools.namespace "0.2.3"]
                                  [org.clojure/java.classpath "0.2.0"]
                                  [org.clojure/tools.logging "0.2.6"]]}}
  :main frereth-renderer.core

  ;; One is needed for core.async.
  ;; I think the other's for jeromq.
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"
                 "sonatype-nexus-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"})
