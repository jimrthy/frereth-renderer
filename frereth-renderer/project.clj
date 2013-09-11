(defn native-classification
  "Because of the way lwjgl is packaged, vs. the way leiningen hopes
native libraries will be packaged, need to specify which platform is
being used. Yes, this approach is pretty horrid.
I wonder how this works with things like uberjars and JNLI.
After all, TWL manages.
Note that this is slightly different from the other examples of
doing pretty much exactly the same thing. I wonder if it has anything
to do with strangeness under Windows 8?
I mean that I had to do extra fiddling that probably shouldn't have
been needed. Most examples just add 'native/platform' to the 
java.library.path and that's good enough."
  []
  (let [sys-name (System/getProperty "os.name")]
    (cond
     (.contains sys-name "Windows") "natives-windows"
     (.contains sys-name "Linux") "natives-linux"
     (.contains sys-name "Solaris") "natives-solaris"
     ;; TODO: What should I expect for this next one?
     ;; TODO: Verify that this works
     (.contains sys-name "Mac") "natives-macosx"
     :else
     ;; Error out ASAP to get this covered.
     (throw (RuntimeException. (str "Unknown environment: " sys-name))))))

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
                 ;; c.f. https://github.com/Syve/lwjgl-test/blob/master/project.clj
                 ;; (This is just a clj file...can differentiate the platform
                 ;; at runtime).
                 ;; Doesn't help with building, but it seems like it'll be 
                 ;; an improvement.
                 [org.lwjgl.lwjgl/lwjgl-platform "2.9.0" 
                  :classifier ~(native-classification) :native-prefix ""]
                 [org.lwjgl.lwjgl/lwjgl_util "2.9.0"]]
  ;; Needed to get to lwjgl native libs...maybe.
  ;; Actually, since leiningen 2.1.0, probably not. This next entry seems
  ;; to be totally obsolete.
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
