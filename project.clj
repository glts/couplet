(defproject ch.gluet/couplet "0.0.1"
  :description "Unicode code points support for Clojure"
  :url "https://github.com/glts/couplet"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/spec.alpha "0.1.123"]
                 [org.clojure/core.specs.alpha "0.1.10"]
                 [org.clojure/test.check "0.9.0"]
                 [criterium "0.4.4"]]
  :plugins [[lein-codox "0.10.3"]])
