(ns couplet.core-test
  (:require [clojure.core.reducers :as r]
            [clojure.spec.alpha :as s]
            [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]
            [couplet.core :as cp]))

(defn- baseline-code-points
  [^CharSequence s]
  (iterator-seq (.iterator (.codePoints s))))

(defn- baseline-to-str
  [cps]
  (let [sb (StringBuilder.)]
    (run! #(.appendCodePoint sb (int %)) cps)
    (str sb)))

(s/def ::ascii (cp/code-point-in 0 127))
(s/def ::emoji (cp/code-point-in 0x1F600 0x1F64F))
(s/def ::surrogate (cp/code-point-in (int Character/MIN_SURROGATE)
                                     (int Character/MAX_SURROGATE)))

(defn- remove-accidental-surrogate-pairs
  [coll]
  (->> coll
       (partition-by #(s/valid? ::surrogate %))
       (map (fn [cps]
              (if (s/valid? ::surrogate (first cps))
                (first (partition-by #(Character/isHighSurrogate (char %)) cps))
                cps)))
       (apply concat)))

(def gen-text
  "A generator of supposedly 'natural' text strings containing a happy mix of
  ASCII characters (including control characters) and Emojis, thus containing
  surrogate pairs, as well as the occasional isolated surrogate. Scaled up to
  include larger strings."
  (->> (gen/frequency [[80 (s/gen ::ascii)]
                       [20 (s/gen ::emoji)]
                       [5 (s/gen ::surrogate)]])
       gen/vector
       (gen/scale #(* % %))
       ;; The frequency generator above occasionally produces two isolated
       ;; surrogates in a row, potentially creating supplementary code points
       ;; that are not Emojis. Remove occurrences of accidental high/low
       ;; surrogate pairs in order to ensure only code points in the expected
       ;; ranges are generated.
       (gen/fmap (comp baseline-to-str remove-accidental-surrogate-pairs))))

(deftest reduce-code-points-succeeds
  (testing "two-argument reduce"
    (is (= []
           (reduce conj (cp/code-points ""))))
    (is (= (int \x)
           (reduce conj (cp/code-points "x"))))
    (is (= 0x1F63C
           (reduce conj (cp/code-points "😼"))))
    (is (= 0xD83D
           (reduce conj (cp/code-points "\ud83d"))))
    (is (= (/ (int \x) (int \y))
           (reduce / (cp/code-points "xy"))))
    (is (= (/ 0xD83D (int \x))
           (reduce / (cp/code-points "\ud83dx"))))
    (is (= (/ 0x1F63C (int \x))
           (reduce / (cp/code-points "😼x")))))

  (testing "three-argument reduce"
    (is (= #{}
           (reduce conj #{} (cp/code-points ""))))
    (is (= #{(int \x)}
           (reduce conj #{} (cp/code-points "x"))))
    (is (= #{0x1F63C}
           (reduce conj #{} (cp/code-points "😼"))))))

(deftest reduce-code-points-halts-when-reduced
  (let [take-two (fn [cps]
                   (transduce (take 2) conj #{} cps))]
    (is (= #{(int \a) 0x1D4C1}
           (take-two (cp/code-points "𝓁aundry"))))
    (is (= #{(int \a) 0x1D4C1}
           (take-two (cp/code-points "a𝓁chemy"))))))

(defspec reduce-code-points-equals-baseline
  (for-all [s gen-text]
    (= (reduce conj [] (baseline-code-points s))
       (reduce conj [] (cp/code-points s)))))

(deftest code-point-seq-print-method-prints-readably
  (let [s "star🌟"
        cps (read-string (pr-str (cp/code-points s)))]
    (is (instance? couplet.core.CodePointSeq cps))
    (is (= s (.s ^couplet.core.CodePointSeq cps)))))

(deftest code-point-str-succeeds
  (is (= "a"
         (cp/code-point-str (int \a))))
  (is (= "😼"
         "\ud83d\ude3c"
         (cp/code-point-str 0x1F63C)))
  (is (= "\ud83d"
         (cp/code-point-str 0xD83D)))
  (is (= "\ude3c"
         (cp/code-point-str 0xDE3C)))
  (is (thrown? IllegalArgumentException
        (cp/code-point-str 0xFFFFFF)))
  (is (thrown? IllegalArgumentException
        (cp/code-point-str -1))))

(deftest to-str-succeeds
  (testing "without transducer"
    (let [abc "abc"]
      (is (= abc
             (cp/to-str (cp/code-points abc)))))
    (let [hammer-rose-dancer "🔨🌹💃"]
      (is (= hammer-rose-dancer
             (cp/to-str (cp/code-points hammer-rose-dancer))))))

  (testing "with transducer"
    (let [hammer-rose-dancer "🔨🌹💃"
          cp-strs (->> (baseline-code-points hammer-rose-dancer)
                       (map cp/code-point-str))]
      (is (= hammer-rose-dancer
             (cp/to-str (map (comp first baseline-code-points)) cp-strs))))))

(defspec to-str-equals-baseline
  (for-all [s gen-text]
    (= s
       (baseline-to-str (cp/code-points s))
       (cp/to-str (cp/code-points s)))))

(deftest fold-succeeds
  (let [cps (cycle [0x12345 67 89])]
    (testing "reduce when below default partition size"
      (let [s (baseline-to-str (take 10 cps))]
        (is (= (into [] (cp/code-points s))
               (r/fold into conj (cp/code-points s))))
        (is (apply + (cp/code-points s))
            (r/fold + (cp/code-points s)))))

    (testing "parallel fold"
      (let [s (baseline-to-str (take 10000 cps))]
        (is (= (into [] (cp/code-points s))
               (r/fold into conj (cp/code-points s))))
        (is (apply + (cp/code-points s))
            (r/fold + (cp/code-points s))))))

  (testing "small partition size"
    (let [s "lem🍋n"]
      (is (= (vec (baseline-code-points s))
             (r/fold 1 into conj (cp/code-points s))))
      (is (= [0x1F34B]
             (r/fold 1 into conj (cp/code-points "🍋"))))
      (is (= []
             (r/fold 1 into conj (cp/code-points "")))))))

(deftest fold-adjusts-split-index-between-surrogates
  (let [count (fn [n _] (inc n))]
    (is (= 7
           (r/fold 4 + count (cp/code-points "abc\ud83d\ude3cdef"))))))

(defspec fold-code-point-frequencies-equals-baseline
  (let [update-freqs #(update %1 %2 (fnil inc 0))
        merge-freqs (r/monoid (partial merge-with +) hash-map)]
    (for-all [s gen-text
              ;; Increase partition size to avoid StackOverflowError due to
              ;; excessive recursion depth during fork/join.
              n (gen/fmap #(+ 8 %) gen/s-pos-int)]
      (= (frequencies (baseline-code-points s))
         (r/fold n merge-freqs update-freqs (cp/code-points s))))))
