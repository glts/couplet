(ns couplet.core-benchmark
  (:require [clojure.spec.gen.alpha :as gen]
            [criterium.core :refer :all]
            [couplet.core :as cp]
            [couplet.core-test :as cptest]))

(defn couplet-codepoints-count
  [s]
  (reduce (fn [n _] (inc n)) 0 (cp/codepoints s)))

(defn couplet-lazy-codepoints-count
  [s]
  (apply + (map (fn [_] 1) (cp/codepoints s))))

(defn clojure-char-count
  [s]
  (reduce (fn [n _] (inc n)) 0 s))

(defn clojure-lazy-char-count
  [s]
  (apply + (map (fn [_] 1) s)))

(defn jdk-char-sequence-code-points-count
  [^CharSequence s]
  (.. s codePoints count))

(defn jdk-char-sequence-chars-count
  [^CharSequence s]
  (.. s chars count))

(defn generate-text
  "Generates a string that consists of exactly n code points, with frequencies
  of code point kind according to the couplet.core-test/gen-weighted-codepoints
  generator (and avoiding introducing accidental supplementary code points)."
  [n]
  (loop [i n
         ret (cp/append!)
         seen-high-surrogate? false]
    (if (zero? i)
      (cp/append! ret)
      (let [cp (gen/generate cptest/gen-weighted-codepoints)]
        (if (and seen-high-surrogate? (cptest/low-surrogate? cp))
          (recur i ret true)
          (recur (dec i) (cp/append! ret cp) (cptest/high-surrogate? cp)))))))

(defn -main
  [& args]
  (let [text (generate-text 1e6)]
    (bench (couplet-codepoints-count text))
    (bench (couplet-lazy-codepoints-count text))
    (bench (clojure-char-count text))
    (bench (clojure-lazy-char-count text))
    (bench (jdk-char-sequence-code-points-count text))
    (bench (jdk-char-sequence-chars-count text))))
