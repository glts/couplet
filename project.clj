(defproject ch.gluet/couplet "0.1.0"
  :description "Unicode code points support for Clojure"
  :url "https://github.com/glts/couplet"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [org.clojure/spec.alpha "0.1.143" :scope "provided"]]
  :plugins [[lein-codox "0.10.3"]
            [lein-jmh "0.2.3"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]]
                   :source-paths ["dev"]
                   :global-vars {*warn-on-reflection* true}}
             :jmh {:jvm-opts []}}

  ;; Adjust Leiningen's JAR build output: do not declare a Main-Class or leak
  ;; the username in the manifest.
  :main nil
  :manifest {"Built-By" "David Bürgin"})
