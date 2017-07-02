(ns couplet.core-benchmark
  (:require [clojure.core.reducers :as r]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [criterium.core :refer :all]
            [couplet.core :as cp]
            [couplet.core-test :as cptest]))

(defn generate-text
  "Generates a string that consists of exactly n code points, with frequencies
  of code point kind according to the couplet.core-test/gen-weighted-codepoints
  generator (and avoiding introducing accidental supplementary code points)."
  [n]
  {:post [(== n (-> % cp/codepoints seq count))
          (->> (cp/codepoints %)
               (filter #(Character/isSupplementaryCodePoint %))
               (every? #(s/valid? ::cptest/emoji %)))]}
  (loop [i n
         ret (cp/append!)
         seen-high-surrogate? false]
    (if (zero? i)
      (cp/append! ret)
      (let [cp (gen/generate cptest/gen-weighted-codepoints)]
        (if (and seen-high-surrogate? (cptest/low-surrogate? cp))
          (recur i ret true)
          (recur (dec i) (cp/append! ret cp) (cptest/high-surrogate? cp)))))))

(defn- format-time
  [fmt s]
  (let [format-scaled (fn [scale unit]
                        (str (format fmt (* scale s)) unit))]
    (condp > s
      1e-6 (format-scaled 1e+9 "ns")
      1e-3 (format-scaled 1e+6 "µs")
      1    (format-scaled 1e+3 "ms")
      (format-scaled 1 "s"))))

(defn- report-tersely
  [name result]
  (let [{:keys [mean variance outliers execution-count sample-count]} result
        outlier-count (apply + (vals outliers))]
    (printf "%5d× %-42s %s±%s%s%n"
            (* sample-count execution-count)
            name
            (format-time "%7.3f" (first mean))
            (format-time "%.2f" (Math/sqrt (first variance)))
            (if (pos? outlier-count)
              (str " (outliers " outlier-count "/" sample-count ")")
              ""))))

(defmacro benchmarking
  "Runs benchmarks for exprs, printing the results in terse, one-line format to
  *out* under the heading given as topic."
  [topic & exprs]
  `(do (newline)
       (println ~topic)
       ~@(map (fn [[n :as expr]]
                `(report-tersely ~(name n) (benchmark ~expr {})))
              exprs)))

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

(defn jdk-char-sequence-chars-count
  [^CharSequence s]
  (.. s chars count))

(defn jdk-char-sequence-code-points-count
  [^CharSequence s]
  (.. s codePoints count))

(defn couplet-fold-frequencies
  [s]
  (let [update-freqs #(update %1 %2 (fnil inc 0))
        merge-freqs (r/monoid (partial merge-with +) hash-map)]
    (r/fold 8192 merge-freqs update-freqs (cp/codepoints s))))

(defn couplet-reduce-frequencies
  [s]
  (reduce #(update %1 %2 (fnil inc 0)) {} (cp/codepoints s)))

(defn couplet-to-str
  [cps]
  (cp/to-str cps))

(defn clojure-apply-str
  [chars]
  (apply str chars))

(defn -main
  [& args]
  (let [text (generate-text 1e6)]
    (benchmarking "Linear count"
      (couplet-codepoints-count text)
      (couplet-lazy-codepoints-count text)
      (clojure-char-count text)
      (clojure-lazy-char-count text)
      (jdk-char-sequence-chars-count text)
      (jdk-char-sequence-code-points-count text))

    (benchmarking "Fold"
      (couplet-fold-frequencies text)
      (couplet-reduce-frequencies text))
    )

  (let [text (generate-text 1e6)
        cps (into [] (cp/codepoints text))
        chars (into [] text)]
    (benchmarking "Accumulation to string"
      (couplet-to-str cps)
      (clojure-apply-str chars)))
  )
