(ns couplet.core-benchmark
  (:require [clojure.core.reducers :as r]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [criterium.core :refer [benchmark]]
            [couplet.core :as cp]
            [couplet.core-test :as cptest]))

(defn generate-text
  "Generates a string that consists of n code points, with frequencies of code
  point kind distributed according to couplet.core-test/gen-weighted-codepoints,
  and avoiding introducing accidental supplementary code points."
  [n]
  {:post [(== n (-> % cp/codepoints seq count))
          (every? #(s/valid? (s/or :ascii ::cptest/ascii
                                   :emoji ::cptest/emoji
                                   :surrogate ::cptest/surrogate) %)
                  (cp/codepoints %))]}
  (loop [i n
         ret (cp/append!)
         high-surrogate-seen? false]
    (if (zero? i)
      (cp/append! ret)
      (let [cp (gen/generate cptest/gen-weighted-codepoints)]
        (if (and high-surrogate-seen? (cptest/low-surrogate? cp))
          (recur i ret true)
          (recur (dec i) (cp/append! ret cp) (cptest/high-surrogate? cp)))))))

(defn generate-ascii-string
  "Generates a string of length n containing only ASCII characters."
  [n]
  {:post [(== n (count %))
          (every? #(s/valid? ::cptest/ascii %) (cp/codepoints %))]}
  (cp/to-str (repeatedly n #(gen/generate (s/gen ::cptest/ascii)))))

(defn- format-execution-time
  [mean sd]
  (let [fmt (fn [scale unit]
              (format "%7.3f%s±%.2f" (* scale mean) unit (* scale sd)))]
    (condp > mean
      1e-6 (fmt 1e+9 "ns")
      1e-3 (fmt 1e+6 "µs")
      1    (fmt 1e+3 "ms")
      (fmt 1 "s"))))

(defn- report-oneline
  [name result]
  (let [{:keys [mean variance outliers execution-count sample-count]} result
        outlier-count (apply + (vals outliers))]
    (printf "%6d× %-40s %s%s%n"
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

(defn couplet-transducing-codepoints-count
  [s]
  (transduce (cp/codepoints) (completing (fn [n _] (inc n))) 0 s))

(defn couplet-lazy-codepoints-count
  [s]
  (apply + (map (fn [_] 1) (cp/codepoints s))))

(defn- lazy-codepoints
  [^CharSequence s]
  (let [cpseq (fn cpseq [i]
                (lazy-seq
                  (when (< i (.length s))
                    (let [c1 (.charAt s i)
                          i (inc i)]
                      (if (and (Character/isHighSurrogate c1)
                               (< i (.length s))
                               (Character/isLowSurrogate (.charAt s i)))
                        (cons (Character/toCodePoint c1 (.charAt s i)) (cpseq (inc i)))
                        (cons (int c1) (cpseq i)))))))]
    (cpseq 0)))

(defn couplet-naive-lazy-codepoints-count
  [s]
  (apply + (map (fn [_] 1) (lazy-codepoints s))))

(defn- chunked-codepoints
  [^CharSequence s]
  (let [cpseq (fn cpseq [i]
                (lazy-seq
                  (when (< i (.length s))
                    (let [buf (chunk-buffer 32)
                          i (loop [i (int i), j (int 32)]
                              (if (and (pos? j) (< i (.length s)))
                                (let [c1 (.charAt s i)
                                      i (inc i)]
                                  (if (and (Character/isHighSurrogate c1)
                                           (< i (.length s))
                                           (Character/isLowSurrogate (.charAt s i)))
                                    (do (chunk-append buf (Character/toCodePoint c1 (.charAt s i)))
                                        (recur (inc i) (dec j)))
                                    (do (chunk-append buf (int c1))
                                        (recur i (dec j)))))
                                i))]
                      (chunk-cons (chunk buf) (cpseq i))))))]
    (cpseq 0)))

(defn couplet-chunked-lazy-codepoints-count
  [s]
  (apply + (map (fn [_] 1) (chunked-codepoints s))))

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

(defn couplet-folding-codepoints-count
  [s]
  (r/fold 8192 + (fn [n _] (inc n)) (cp/codepoints s)))

(defn- update-freqs [freqs k]
  (update freqs k (fnil inc 0)))

(defn couplet-fold-frequencies
  [n s]
  (let [merge-freqs (r/monoid (partial merge-with +) hash-map)]
    (r/fold n merge-freqs update-freqs (cp/codepoints s))))

(defn couplet-fold-frequencies-256 [s] (couplet-fold-frequencies 256 s))
(defn couplet-fold-frequencies-2048 [s] (couplet-fold-frequencies 2048 s))
(defn couplet-fold-frequencies-8192 [s] (couplet-fold-frequencies 8192 s))
(defn couplet-fold-frequencies-131072 [s] (couplet-fold-frequencies 131072 s))

(defn couplet-reduce-frequencies
  [s]
  (reduce update-freqs {} (cp/codepoints s)))

(defn- ascii? [cp]
  (<= 0 cp 127))

(defn- fold-into-set [coll]
  (r/fold 8192 (r/monoid into hash-set) conj coll))

(defn couplet-reducer-foldcat
  [s]
  (let [intermediate-coll (->> (cp/codepoints s)
                               (r/filter ascii?)
                               (r/fold 8192 r/cat r/append!))]
    ((juxt count fold-into-set) intermediate-coll)))

(defn couplet-reducer-fold-combining
  [s]
  (let [intermediate-coll (->> (cp/codepoints s)
                               (r/filter ascii?)
                               (r/fold 8192 into conj))]
    ((juxt count fold-into-set) intermediate-coll)))

(defn couplet-reducer-sequential
  [s]
  (let [intermediate-coll (->> (cp/codepoints s)
                               (r/filter ascii?)
                               (into []))]
    ((juxt count fold-into-set) intermediate-coll)))

(defn couplet-to-str
  [cps]
  (cp/to-str cps))

(defn couplet-to-str-with-transducer
  [cps]
  (cp/to-str (remove cptest/high-surrogate?) cps))

(defn clojure-apply-str
  [chars]
  (apply str chars))

(defn clojure-apply-str-with-filter
  [chars]
  (apply str (remove #(cptest/high-surrogate? (int %)) chars)))

(def ^:private generators {"mixed text" generate-text
                           "ASCII" generate-ascii-string})

(defn -main
  [& _]
  (doseq [[description generate] generators]
    (let [s (generate 1e6)]
      (benchmarking (str "Reduce/iterate " description)
        (couplet-codepoints-count s)
        (couplet-transducing-codepoints-count s)
        (couplet-lazy-codepoints-count s)
        (couplet-naive-lazy-codepoints-count s)
        (couplet-chunked-lazy-codepoints-count s)
        (clojure-char-count s)
        (clojure-lazy-char-count s)
        (jdk-char-sequence-chars-count s)
        (jdk-char-sequence-code-points-count s))))

  (let [text (generate-text 1e6)]
    (benchmarking "Fold"
      (couplet-folding-codepoints-count text)
      (couplet-fold-frequencies 8192 text)
      (couplet-reduce-frequencies text)))

  (let [text (generate-text 1e6)]
    (benchmarking "Fold with different partition sizes"
      (couplet-fold-frequencies-256 text)
      (couplet-fold-frequencies-2048 text)
      (couplet-fold-frequencies-8192 text)
      (couplet-fold-frequencies-131072 text)))

  (let [text (generate-text 1e6)]
    (benchmarking "Fold reducer"
      (couplet-reducer-foldcat text)
      (couplet-reducer-fold-combining text)
      (couplet-reducer-sequential text)))

  (doseq [[description generate] generators]
    (let [s (generate 1e6)
          cps (into [] (cp/codepoints s))
          chars (into [] s)]
      (benchmarking (str "Accumulate " description " string")
        (couplet-to-str cps)
        (couplet-to-str-with-transducer cps)
        (clojure-apply-str chars)
        (clojure-apply-str-with-filter chars)))))
