(ns couplet.core-benchmark
  (:require [clojure.core.reducers :as r]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [couplet.core :as cp]
            [couplet.core-test :as cptest]))

(defn reducing-codepoints-count
  [s]
  (reduce (fn [n _] (inc n)) 0 (cp/codepoints s)))

(defn transducing-codepoints-count
  [s]
  (transduce (cp/codepoints) (completing (fn [n _] (inc n))) 0 s))

(defn lazy-codepoints-count
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

(defn naive-lazy-codepoints-count
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

(defn chunked-lazy-codepoints-count
  [s]
  (apply + (map (fn [_] 1) (chunked-codepoints s))))

(defn folding-codepoints-count
  [s]
  (r/fold 8192 + (fn [n _] (inc n)) (cp/codepoints s)))

(defn clojure-char-count
  [s]
  (reduce (fn [n _] (inc n)) 0 s))

(defn clojure-lazy-char-count
  [s]
  (apply + (map (fn [_] 1) s)))

(def ^:private count-int-op
  (reify java.util.function.IntBinaryOperator
    (applyAsInt [this n _]
      (inc n))))

(defn jdk-char-sequence-chars-count
  [^CharSequence s]
  (.. s chars (reduce 0 count-int-op)))

(defn jdk-char-sequence-code-points-count
  [^CharSequence s]
  (.. s codePoints (reduce 0 count-int-op)))

(defn- update-freqs [freqs k]
  (update freqs k (fnil inc 0)))

(defn reduce-frequencies
  [s]
  (reduce update-freqs {} (cp/codepoints s)))

(defn fold-frequencies
  [n s]
  (let [merge-freqs (r/monoid (partial merge-with +) hash-map)]
    (r/fold n merge-freqs update-freqs (cp/codepoints s))))

(defn- ascii? [cp]
  (<= 0 cp 127))

(defn- fold-into-set [coll]
  (r/fold 8192 (r/monoid into hash-set) conj coll))

(defn reducer-foldcat
  [s]
  (let [intermediate-coll (->> (cp/codepoints s)
                               (r/filter ascii?)
                               (r/fold 8192 r/cat r/append!))]
    ((juxt count fold-into-set) intermediate-coll)))

(defn reducer-fold-combining
  [s]
  (let [intermediate-coll (->> (cp/codepoints s)
                               (r/filter ascii?)
                               (r/fold 8192 into conj))]
    ((juxt count fold-into-set) intermediate-coll)))

(defn reducer-sequential
  [s]
  (let [intermediate-coll (->> (cp/codepoints s)
                               (r/filter ascii?)
                               (into []))]
    ((juxt count fold-into-set) intermediate-coll)))

(defn to-str
  [codepoints]
  (cp/to-str codepoints))

(defn transducing-to-str
  [codepoints]
  (cp/to-str (remove cptest/high-surrogate?) codepoints))

(defn clojure-apply-str
  [chars]
  (apply str chars))

(defn clojure-apply-str-with-filter
  [chars]
  (apply str (remove #(cptest/high-surrogate? (int %)) chars)))

(defmacro ^:private some-codepoint [codepoint-spec & more]
  `(some-fn ~@(map s/form (cons codepoint-spec more))))

(defn gen-text
  "Generates a string that consists of n code points, with frequencies of code
  point kind distributed according to couplet.core-test/gen-weighted-codepoints,
  and avoiding introducing accidental supplementary code points."
  [n]
  {:post [(== n (-> % cp/codepoints seq count))
          (every? (some-codepoint ::cptest/ascii
                                  ::cptest/emoji
                                  ::cptest/surrogate)
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

(defn gen-ascii
  "Generates a string of length n containing only ASCII characters."
  [n]
  {:post [(== n (count %))
          (every? (some-codepoint ::cptest/ascii) (cp/codepoints %))]}
  (cp/to-str (repeatedly n #(gen/generate (s/gen ::cptest/ascii)))))

(defn generate-string
  "Delegates generation of string inputs to the fn to which gen-sym resolves."
  [gen-sym length]
  ((ns-resolve 'couplet.core-benchmark gen-sym) length))
