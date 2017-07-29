(ns couplet.core-profile
  (:require [couplet.core-benchmark :as cpbench])
  (:gen-class))

;; This stub can be used to profile benchmark functions. Compile this namespace
;; from a REPL with (compile 'couplet.core-profile), then run the main class
;; couplet.core_profile with a profiler.

(defn -main
  [& _]
  (let [s (cpbench/generate-text 1e5)]
    (dotimes [i 100]
      (cpbench/couplet-codepoints-count s))))
