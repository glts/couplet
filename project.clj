(defproject ch.gluet/couplet "0.0.4"
  :description "Unicode code points support for Clojure"
  :url "https://github.com/glts/couplet"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.9.0-alpha17" :scope "provided"]
                 [org.clojure/spec.alpha "0.1.123" :scope "provided"]]
  :plugins [[lein-codox "0.10.3"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [criterium "0.4.4"]]}
             :benchmark {:main couplet.core-benchmark
                         :source-paths ["benchmark"]
                         :jvm-opts ^:replace []}}

  ;; Adjust Leiningen's JAR build output: do not declare a Main-Class or leak
  ;; the login name in the manifest, and omit unnecessarily included file.
  :main nil
  :jar-exclusions [#"^project\.clj$"]
  :manifest {"Built-By" "David Bürgin"})
