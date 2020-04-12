(defproject ch.gluet/couplet "0.2.1"
  :description "Unicode code points support for Clojure"
  :url "https://github.com/glts/couplet"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]
                 [org.clojure/spec.alpha "0.2.187" :scope "provided"]]
  :plugins [[lein-codox "0.10.7"]
            [lein-jmh "0.2.6"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "1.0.0"]]
                   :source-paths ["dev"]}
             :jmh {:jvm-opts []}}

  ;; Adjust Leiningen's JAR build output: do not declare a Main-Class or leak
  ;; the username in the manifest.
  :main nil
  :manifest {"Built-By" "David Bürgin"})
