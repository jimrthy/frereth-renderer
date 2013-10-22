(comment (defn native-classification
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
              (throw (RuntimeException. (str "Unknown environment: " sys-name)))))))


(defproject frereth-renderer "0.0.1-SNAPSHOT"
  :description "A renderer suitable for frereth, clojure style."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  ;; Big swaths of these are only needed because I haven't found the time
  ;; to properly configure my local maven repo.
  :dependencies [[byte-transforms "0.1.0"]
                 [jimrthy/cljeromq "0.1.0-SNAPSHOT"]
                 [jimrthy/penumbra "0.6.5-SNAPSHOT"]
                 [kephale/cantor "0.4.1"] ; Deprecated math optimization library
                 [kephale/lwjgl "2.9.0"]
                 [kephale/lwjgl-natives "2.9.0"]
                 [kephale/lwjgl-util "2.9.0"]
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.222.0-83d0c2-alpha"]
                 [org.clojure/core.contracts "0.0.5"]
                 [org.clojure/math.combinatorics "0.0.2"]
                 [org.jeromq/jeromq "0.3.0-SNAPSHOT"]
                 [org.zeromq/cljzmq "0.1.1" :exclusions [org.zeromq/jzmq]]
                 ;; TODO: An nrepl client?

                 ;; FIXME: Is this more appropriate here or in frereth-client?
                 ;; Is it worth an external dependency at all? Especially since
                 ;; this *is* such an important part of frereth's core?
                 [simplecs "0.1.0"]
                 [slick-util "1.0.0"]
                 [slingshot "0.10.3"]
                 [spyscope "0.1.3"]]
  ;; Needed to get to lwjgl native libs...is this still true w/ penumbra?
  ;; Actually, since leiningen 2.1.0, probably not. This next entry seems
  ;; to be totally obsolete.
  :jvm-opts [~(str "-Djava.library.path=native/:"
                   (System/getProperty "java.library.path"))]
  :main frereth-renderer.core

  ;;:plugins [[lein-git-deps "0.0.1-SNAPSHOT"]]
  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :dependencies  [[midje "1.5.1"]
                                   [night-vision "0.1.0-SNAPSHOT"]
                                   [org.clojure/tools.namespace "0.2.3"]
                                   [org.clojure/java.classpath "0.2.0"]
                                   ;; Umm...do I really not want this for
                                   ;; real??
                                   [org.clojure/tools.logging "0.2.6"]]
                   ;; c.f. https://gist.github.com/MichaelDrogalis/6638777
                   :injections [(require 'night-vision.goggles)
                                (require 'clojure.pprint)]}}
  :repl-options {:init-ns user}
  ;; FIXME: these are both experimental repos and should go away.
  ;; One was needed for core.async.
  ;; I think the other's for jeromq.
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"
                 "sonatype-nexus-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"})
