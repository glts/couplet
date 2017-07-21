(ns couplet.core
  "Unicode code points support for Clojure.

  Couplet provides support for treating CharSequences (such as strings) as
  sequences of Unicode characters or 'code points'. It includes a few additional
  utilities to make working with code points a little more pleasant."
  (:require [clojure.core.protocols :refer [CollReduce]]
            [clojure.core.reducers :as r]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen])
  (:import [clojure.lang Sequential]
           [java.io Writer]
           [java.util.concurrent Callable ForkJoinPool ForkJoinTask]))

(defn- codepoint-reduce
  [^CharSequence s i f val]
  (loop [i (int i)
         ret val]
    (if (< i (.length s))
      (let [c1 (.charAt s i)
            i (inc i)]
        (if (and (Character/isHighSurrogate c1)
                 (< i (.length s))
                 (Character/isLowSurrogate (.charAt s i)))
          (let [ret (f ret (Character/toCodePoint c1 (.charAt s i)))]
            (if (reduced? ret)
              @ret
              (recur (inc i) ret)))
          (let [ret (f ret (int c1))]
            (if (reduced? ret)
              @ret
              (recur i ret)))))
      ret)))

(deftype CodePointSeq [^CharSequence s]
  Sequential

  Iterable
  (iterator [_]
    (.iterator (.codePoints s)))

  CollReduce
  (coll-reduce [_ f]
    (case (.length s)
      0 (f)
      1 (int (.charAt s 0))
      2 (if (and (Character/isHighSurrogate (.charAt s 0))
                 (Character/isLowSurrogate (.charAt s 1)))
          (Character/toCodePoint (.charAt s 0) (.charAt s 1))
          (codepoint-reduce s 1 f (int (.charAt s 0))))
      (if (and (Character/isHighSurrogate (.charAt s 0))
               (Character/isLowSurrogate (.charAt s 1)))
        (codepoint-reduce s 2 f (Character/toCodePoint (.charAt s 0) (.charAt s 1)))
        (codepoint-reduce s 1 f (int (.charAt s 0))))))
  (coll-reduce [_ f val]
    (if (zero? (.length s))
      val
      (codepoint-reduce s 0 f val))))

(defmethod print-method CodePointSeq
  [^CodePointSeq cps ^Writer w]
  (.write w "#couplet.core.CodePointSeq")
  (print-method (vector (str (.s cps))) w))

(defn codepoints
  "Returns a value that acts like a sequence of code points, wrapping the given
  CharSequence s.

  The result is of type couplet.core.CodePointSeq, a type which is seqable,
  reducible, and foldable. The wrapped CharSequence is treated as immutable (like
  a string).

  Unlike CharSequence, CodePointSeq is not counted? and does not support random
  access. Use seq to obtain a regular (lazy) seq of code points."
  [s]
  {:pre [(some? s)]}
  (->CodePointSeq s))

(defn codepoint?
  "Returns true if x is a code point.

  See also the spec :couplet.core/codepoint, which has an associated generator."
  [x]
  (and (int? x) (<= Character/MIN_CODE_POINT x Character/MAX_CODE_POINT)))

(defmacro codepoint-in
  "Returns a spec that validates code points in the range from start to end
  inclusive.

  See also the predefined spec :couplet.core/codepoint, which validates all code
  points."
  [start end]
  `(s/spec #(s/int-in-range? ~start (inc ~end) %)
     :gen #(gen/fmap int (gen/choose ~start ~end))))

(s/def ::codepoint
  (codepoint-in Character/MIN_CODE_POINT Character/MAX_CODE_POINT))

(defn codepoint-str
  "Returns a string containing the Unicode character specified by code point cp."
  [cp]
  (String/valueOf (Character/toChars cp)))

(s/fdef codepoint-str
  :args (s/cat :codepoint ::codepoint)
  :ret string?
  :fn #(= (count (:ret %))
          (if (Character/isBmpCodePoint (-> % :args :codepoint)) 1 2)))

(defn append!
  "Reducing function applicable to code point input, with accumulation based on
  (mutable) StringBuilder.

  Primarily for use as reducing function in reduce and transduce. For example:
  (transduce xf append! (codepoints \"abc\"))"
  ([] (StringBuilder.))
  ([^StringBuilder sb] (.toString sb))
  ([^StringBuilder sb cp] (.appendCodePoint sb (int cp))))

(defn to-str
  "Returns a string containing the code points in coll. When a transducer is
  supplied, applies the transform to the inputs before appending them to the
  result.

  This is a convenience function around reduce/transduce with reducing function
  append!, so coll must either directly or by way of transformation through xform
  consist of Unicode code points."
  ([coll]
   (to-str identity coll))
  ([xform coll]
   (transduce xform append! coll)))

(defn- fork-join-task
  ^ForkJoinTask [^Callable f]
  (ForkJoinTask/adapt f))

(defn- fold-codepoints
  [^CharSequence s start end n combinef reducef]
  (if (or (<= (- end start) n)
          (and (= (- end start) 2)
               (Character/isHighSurrogate (.charAt s start))
               (Character/isLowSurrogate (.charAt s (inc start)))))
    (reduce reducef (combinef) (->CodePointSeq (.subSequence s start end)))
    (let [split (+ start (quot (- end start) 2))
          split (cond-> split
                  (and (Character/isHighSurrogate (.charAt s (dec split)))
                       (Character/isLowSurrogate (.charAt s split)))
                  inc)
          task (fork-join-task
                 #(fold-codepoints s split end n combinef reducef))]
      (.fork task)
      (combinef (fold-codepoints s start split n combinef reducef)
                (.join task)))))

;; Note that partition size n is based on chars, not code points.
(extend-type CodePointSeq
  r/CollFold
  (coll-fold [cps n combinef reducef]
    (let [^CharSequence s (.s cps)]
      (cond
        (zero? (.length s))
        (combinef)

        (<= (.length s) n)
        (reduce reducef (combinef) cps)

        :else
        (.invoke ^ForkJoinPool @r/pool
                 (fork-join-task
                   #(fold-codepoints s 0 (.length s) n combinef reducef)))))))
