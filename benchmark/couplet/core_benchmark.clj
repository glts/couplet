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

(defn generate-ascii-string
  "Generates a string of length n containing only ASCII characters."
  [n]
  {:post [(== n (count %))
          (every? #(s/valid? ::cptest/ascii %) (cp/codepoints %))]}
  (cp/to-str (repeatedly n #(gen/generate (s/gen ::cptest/ascii)))))

(defn- format-execution-time
  [mean stddev]
  (let [fmt (fn [scale unit]
              (format "%7.3f%s±%.2f" (* scale mean) unit (* scale stddev)))]
    (condp > mean
      1e-6 (fmt 1e+9 "ns")
      1e-3 (fmt 1e+6 "µs")
      1    (fmt 1e+3 "ms")
      (fmt 1 "s"))))

(defn- report-oneline
  [name result]
  (let [{:keys [mean variance outliers execution-count sample-count]} result
        outlier-count (apply + (vals outliers))]
    (printf "%5d× %-42s %s%s%n"
            (* sample-count execution-count)
            name
            (format-execution-time (first mean) (Math/sqrt (first variance)))
            (if (pos? outlier-count)
              (str " (outliers " outlier-count "/" sample-count ")")
              ""))))

(defmacro benchmarking
  "Runs benchmarks for exprs, printing the results in one-line format to *out*
  under the heading given as topic."
  [topic & exprs]
  `(do (newline)
       (println ~topic)
       ~@(map (fn [[n :as expr]]
                `(report-oneline ~(name n) (benchmark ~expr {})))
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
  ([n s]
   (let [update-freqs #(update %1 %2 (fnil inc 0))
         merge-freqs (r/monoid (partial merge-with +) hash-map)]
     (r/fold n merge-freqs update-freqs (cp/codepoints s)))))

(defn couplet-fold-frequencies-256 [s] (couplet-fold-frequencies 256 s))
(defn couplet-fold-frequencies-2048 [s] (couplet-fold-frequencies 2048 s))
(defn couplet-fold-frequencies-8192 [s] (couplet-fold-frequencies 8192 s))
(defn couplet-fold-frequencies-131072 [s] (couplet-fold-frequencies 131072 s))

(defn couplet-reduce-frequencies
  [s]
  (reduce #(update %1 %2 (fnil inc 0)) {} (cp/codepoints s)))

(defn couplet-to-str
  [cps]
  (cp/to-str cps))

(defn clojure-apply-str
  [chars]
  (apply str chars))

(def ^:private generators {"text"  generate-text
                           "ASCII" generate-ascii-string})

(defn -main
  [& args]
  (doseq [[description generate] generators]
    (let [s (generate 1e6)]
      (benchmarking (str "Reduce/iterate " description)
        (couplet-codepoints-count s)
        (couplet-lazy-codepoints-count s)
        (clojure-char-count s)
        (clojure-lazy-char-count s)
        (jdk-char-sequence-chars-count s)
        (jdk-char-sequence-code-points-count s))))

  (let [s (generate-text 1e6)]
    (benchmarking "Fold"
      (couplet-fold-frequencies 8192 s)
      (couplet-reduce-frequencies s)))

  (let [s (generate-text 1e6)]
    (benchmarking "Fold with different partition sizes"
      (couplet-fold-frequencies-256 s)
      (couplet-fold-frequencies-2048 s)
      (couplet-fold-frequencies-8192 s)
      (couplet-fold-frequencies-131072 s)))

  (doseq [[description generate] generators]
    (let [s (generate 1e6)
          cps (into [] (cp/codepoints s))
          chars (into [] s)]
      (benchmarking (str "Accumulate " description " string")
        (couplet-to-str cps)
        (clojure-apply-str chars))))
  )
