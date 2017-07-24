(ns couplet.core-test
  (:require [clojure.core.reducers :as r]
            [clojure.spec.alpha :as s]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]
            [couplet.core :as cp]))

(defn- baseline-codepoints
  [^CharSequence s]
  (iterator-seq (.iterator (.codePoints s))))

(defn- baseline-to-str
  [cps]
  (let [sb (StringBuilder.)]
    (run! #(.appendCodePoint sb (int %)) cps)
    (str sb)))

(s/def ::ascii (cp/codepoint-in 0 127))
(s/def ::emoji (cp/codepoint-in 0x1F600 0x1F64F))
(s/def ::surrogate (cp/codepoint-in (int Character/MIN_SURROGATE)
                                    (int Character/MAX_SURROGATE)))

(def gen-weighted-codepoints
  (gen/frequency [[80 (s/gen ::ascii)]
                  [20 (s/gen ::emoji)]
                  [5 (s/gen ::surrogate)]]))

(defn high-surrogate? [cp]
  (<= (int Character/MIN_HIGH_SURROGATE) cp (int Character/MAX_HIGH_SURROGATE)))

(defn low-surrogate? [cp]
  (<= (int Character/MIN_LOW_SURROGATE) cp (int Character/MAX_LOW_SURROGATE)))

(defn- remove-accidental-surrogate-pairs
  [coll]
  {:post [(not-any? (fn [[h l]]
                      (and (high-surrogate? h) (low-surrogate? l)))
                    (partition 2 1 %))]}
  (->> coll
       (partition-by #(s/valid? ::surrogate %))
       (map (fn [cps]
              (if (s/valid? ::surrogate (first cps))
                (first (partition-by #(Character/isHighSurrogate (char %)) cps))
                cps)))
       (apply concat)))

(def gen-text
  "A generator of supposedly 'natural' text strings containing a happy mix of
  ASCII characters (including control characters) and emoji, thus containing
  surrogate pairs, as well as the occasional isolated surrogate. Scaled up to
  include larger strings."
  (->> gen-weighted-codepoints
       gen/vector
       (gen/scale #(* % %))
       ;; The frequency generator above occasionally produces two isolated
       ;; surrogates in a row, potentially creating supplementary code points
       ;; that are not emoji. Remove occurrences of accidental high/low
       ;; surrogate pairs in order to ensure only code points in the expected
       ;; ranges are generated.
       (gen/fmap (comp baseline-to-str remove-accidental-surrogate-pairs))))

(deftest reduce-codepoints-succeeds
  (testing "two-argument reduce"
    (is (= []
           (reduce conj (cp/codepoints ""))))
    (is (= (int \x)
           (reduce conj (cp/codepoints "x"))))
    (is (= 0x1F63C
           (reduce conj (cp/codepoints "😼"))))
    (is (= 0xD83D
           (reduce conj (cp/codepoints "\ud83d"))))
    (is (= (/ (int \x) (int \y))
           (reduce / (cp/codepoints "xy"))))
    (is (= (/ 0xD83D (int \x))
           (reduce / (cp/codepoints "\ud83dx"))))
    (is (= (/ 0x1F63C (int \x))
           (reduce / (cp/codepoints "😼x")))))

  (testing "three-argument reduce"
    (is (= #{}
           (reduce conj #{} (cp/codepoints ""))))
    (is (= #{(int \x)}
           (reduce conj #{} (cp/codepoints "x"))))
    (is (= #{0x1F63C}
           (reduce conj #{} (cp/codepoints "😼"))))))

(deftest reduce-codepoints-halts-when-reduced
  (let [take-two (fn [cps]
                   (transduce (take 2) conj #{} cps))]
    (is (= #{(int \a) 0x1D4C1}
           (take-two (cp/codepoints "𝓁aundry"))))
    (is (= #{(int \a) 0x1D4C1}
           (take-two (cp/codepoints "a𝓁chemy"))))))

(defspec reduce-codepoints-equals-baseline
  (for-all [s gen-text]
    (= (reduce conj [] (baseline-codepoints s))
       (reduce conj [] (cp/codepoints s)))))

(defn- conj-taking [n]
  (let [i (atom n)]
    (fn
      ([] [])
      ([result]
       {:pre [(not (reduced? result))]}
       result)
      ([result input]
       {:pre [(not (reduced? result))
              (pos? @i)]}
       (if (zero? (swap! i dec))
         (reduced (conj result input))
         (conj result input))))))

(deftest codepoints-transducer-succeeds
  (testing "transform char inputs"
    (is (= [(int \a) (int \b) (int \c)]
           (transduce (cp/codepoints) conj "abc")))
    (is (= [0x1F528 0x1F339 0x1F483]
           (transduce (cp/codepoints) conj "🔨🌹💃")))
    (is (= [0xD83D]
           (transduce (cp/codepoints) conj "\ud83d"))))

  (testing "reducing function not called in completion when reduced"
    (is (= [0xD83D]
           (transduce (cp/codepoints) (conj-taking 1) [] "\ud83d\ud83d"))))

  (testing "completion arity unreduces reduced value"
    (is (= [0xD83D]
           (transduce (cp/codepoints) (conj-taking 1) [] "\ud83d")))))

(defspec sequence-from-codepoints-transducer-equals-baseline
  (for-all [s gen-text]
    (= (sequence (baseline-codepoints s))
       (sequence (cp/codepoints) s))))

(defspec codepoints-transducer-handles-reduced-results
  (for-all [s gen-text
            n gen/s-pos-int]
    (= (vec (take n (baseline-codepoints s)))
       (transduce (comp (cp/codepoints) (take n)) conj s)
       (transduce (cp/codepoints) (conj-taking n) s))))

(deftest codepoint-seq-print-method-prints-readably
  (let [s "star🌟"
        cps (read-string (pr-str (cp/codepoints s)))]
    (is (instance? couplet.core.CodePointSeq cps))
    (is (= s (.s ^couplet.core.CodePointSeq cps)))))

(deftest codepoint-str-succeeds
  (is (= "a"
         (cp/codepoint-str (int \a))))
  (is (= "😼"
         "\ud83d\ude3c"
         (cp/codepoint-str 0x1F63C)))
  (is (= "\ud83d"
         (cp/codepoint-str 0xD83D)))
  (is (= "\ude3c"
         (cp/codepoint-str 0xDE3C)))
  (is (thrown? IllegalArgumentException
        (cp/codepoint-str 0xFFFFFF)))
  (is (thrown? IllegalArgumentException
        (cp/codepoint-str -1))))

(deftest to-str-succeeds
  (testing "without transducer"
    (let [abc "abc"]
      (is (= abc
             (cp/to-str (cp/codepoints abc)))))
    (let [hammer-rose-dancer "🔨🌹💃"]
      (is (= hammer-rose-dancer
             (cp/to-str (cp/codepoints hammer-rose-dancer))))))

  (testing "with transducer"
    (let [hammer-rose-dancer "🔨🌹💃"
          cp-strs (->> (baseline-codepoints hammer-rose-dancer)
                       (map cp/codepoint-str))]
      (is (= hammer-rose-dancer
             (cp/to-str (map (comp first baseline-codepoints)) cp-strs))))))

(defspec to-str-equals-baseline
  (for-all [s gen-text]
    (= s
       (baseline-to-str (cp/codepoints s))
       (cp/to-str (cp/codepoints s)))))

(deftest fold-succeeds
  (let [cps (cycle [0x12345 67 89])]
    (testing "reduce when below default partition size"
      (let [s (baseline-to-str (take 10 cps))]
        (is (= (into [] (cp/codepoints s))
               (r/fold into conj (cp/codepoints s))))
        (is (= (apply + (cp/codepoints s))
               (r/fold + (cp/codepoints s))))))

    (testing "parallel fold"
      (let [s (baseline-to-str (take 10000 cps))]
        (is (= (into [] (cp/codepoints s))
               (r/fold into conj (cp/codepoints s))))
        (is (= (apply + (cp/codepoints s))
               (r/fold + (cp/codepoints s)))))))

  (testing "small partition size"
    (let [s "lem🍋n"]
      (is (= (vec (baseline-codepoints s))
             (r/fold 1 into conj (cp/codepoints s))))
      (is (= [0x1F34B]
             (r/fold 1 into conj (cp/codepoints "🍋"))))
      (is (= []
             (r/fold 1 into conj (cp/codepoints "")))))))

(deftest fold-adjusts-split-index-between-surrogates
  (let [count (fn [n _] (inc n))]
    (is (= 7
           (r/fold 4 + count (cp/codepoints "abc\ud83d\ude3cdef"))))))

(defspec fold-codepoint-frequencies-equals-baseline
  (let [update-freqs #(update %1 %2 (fnil inc 0))
        merge-freqs (r/monoid (partial merge-with +) hash-map)]
    (for-all [s gen-text
              n gen/s-pos-int]
      (= (frequencies (baseline-codepoints s))
         (r/fold n merge-freqs update-freqs (cp/codepoints s))))))
