(ns couplet.core-test
  (:require [clojure.core.protocols :refer [coll-reduce]]
            [clojure.core.reducers :as r]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer :all]
            [clojure.test.check :refer [quick-check]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :refer [defspec]]
            [couplet.core :as cp]))

(deftest reduce-code-points
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
