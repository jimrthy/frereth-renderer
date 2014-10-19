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

  ;; N.B.: Several of these are used by application, when view-manager
  ;; loads them up with a separate class loader. When you bump versions here,
  ;; double-check there.
  ;; TODO: Seems like it would be better to have that just load everything
  ;; listed here instead.
  :dependencies [[byte-streams "0.1.13"]
                 [byte-transforms "0.1.3"]
                 [com.badlogicgames.gdx/gdx "1.3.1"]
                 [com.badlogicgames.gdx/gdx-backend-lwjgl "1.3.1"]
                 [com.badlogicgames.gdx/gdx-box2d "1.3.1"]
                 [com.badlogicgames.gdx/gdx-box2d-platform "1.3.1"
                  :classifier "natives-desktop"]
                 [com.badlogicgames.gdx/gdx-bullet "1.3.1"]
                 [com.badlogicgames.gdx/gdx-bullet-platform "1.3.1"
                  :classifier "natives-desktop"]
                 [com.badlogicgames.gdx/gdx-platform "1.3.1"
                  :classifier "natives-desktop"]
                 [com.cemerick/pomegranate "0.3.0"]
                 ;; clojars claims that it has this available for download
                 [com.datomic/datomic-free "0.9.4899"]
                 [com.stuartsierra/component "0.2.2"]
                 [com.taoensso/timbre "3.3.1"]
                 ;; I want something like the next line, but it somehow
                 ;; manages to make hiccup conflict with itself
                 ;; (I blame this complicated project, not the library
                 ;; I want to use...after all, why on earth am I
                 ;; using hiccup?)
                 ;[datomic-schema-grapher "0.0.1"]
                 [frereth-client "0.1.0-SNAPSHOT"]
                 [frereth-common "0.0.1-SNAPSHOT"]
                 [im.chit/ribol "0.4.0"]
                 ;;[jimrthy/cljeromq "0.1.0-SNAPSHOT"]  ; Q: Go away, or not?
                 [org.clojure/clojure "1.6.0"]  ; 1.7 breaks datomic
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [org.clojure/core.contracts "0.0.5"]  ; TODO: Make this go away
                 ;; TODO: What is this?
                 [org.clojure/math.combinatorics "0.0.7"]
                 [org.flatland/protobuf "0.8.1"]
                 [org.flatland/useful "0.11.2"]
                 [org.zeromq/cljzmq "0.1.4"]
                 [play-clj "0.3.11"]
                 ;; TODO: An nrepl client?
                 [prismatic/plumbing "0.3.3"]
                 [prismatic/schema "0.3.0"]

                 ;; FIXME: Is this more appropriate here or in frereth-client?
                 ;; Is it worth an external dependency at all? Especially since
                 ;; this *is* such an important part of frereth's core?
                 ;;[simplecs "0.1.0"]
                 ;; For pulling things like image, sound, and font files into
                 ;; LWJGL. I really shouldn't need this here. Seems like
                 ;; it most likely implies a missing dependency in
                 ;; penumbra.
                 ;; TODO: Figure out where this actually belongs.
                 ;;[slick-util "1.0.0"]
                 ;; Probably does not belong at all in the "new reality"
                 ;; of using libgdx as the foundation
                 ]
  :documentation {:files {"basics" {:input "test/docs/basics.clj"
                                    :title "Basics"
                                    :sub-title "Wrapping my head around the documentation parts"
                                    :author "James Gatannah"
                                    :email "james@frereth.com"}}}
  :jvm-opts [~(str "-Djava.library.path=native/:/usr/local/lib:"
                   (System/getProperty "java.library.path"))]  ; for jzmq
  :main frereth-renderer.core

  :plugins [[lein-protobuf "0.4.1"]]  ;; TODO: Do I want to try to use that?

  :profiles {:uberjar {:aot :all}
             :dev {:source-paths ["dev"]
                   :dependencies  [[alembic "0.3.2"]
                                   [clj-ns-browser "1.3.1"]
                                   ;;[com.ambrosebs/dynalint "0.1.3"]
                                   [midje "1.6.3"]
                                   [org.clojure/tools.namespace "0.2.6"]
                                   [org.clojure/java.classpath "0.2.2"]
                                   ;; Umm...do I really not want this for
                                   ;; real??
                                   [org.clojure/tools.logging "0.2.6"]]}}
  :repl-options {:init-ns user}
  ;; FIXME: these are both experimental repos and should go away.
  ;; One was needed for core.async.
  ;; I think the other's for jeromq.
  ;; TODO: Make them go away and see what breaks
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"
                 "sonatype-nexus-snapshots" "https://oss.sonatype.org/content/repositories/snapshots"})
