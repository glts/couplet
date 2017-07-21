;; This is a short, interactive walkthrough of most of the functionality
;; provided by Couplet. For the best experience, evaluate the expressions as you
;; go along.


;; Namespace

;; We begin by requiring the main namespace, couplet.core, aliasing it as 'cp'.
;; Just for this walkthrough, let's also :refer the few functions from that
;; namespace. The names have been chosen such that both namespace aliasing and
;; referring can be used, according with your preference.
(require '[couplet.core :as cp :refer :all])


;; Basics

;; The central function of Couplet is 'codepoints'. It returns a value of type
;; CodePointSeq wrapping the given string.
(codepoints "bird 🐦")

;; A CodePointSeq behaves like a sequence of Unicode characters, or 'code
;; points' (the term preferred in the JDK). Use seq to obtain a lazy seq of code
;; points. Once you have the seq of code points you can begin filtering/mapping/
;; reducing right away: code points in Javaland are simply integers.
(seq (codepoints "bird 🐦"))
(map type (codepoints "bird 🐦"))

;; The meaning of code point integers is opaque, so there must be a way of
;; getting back from the integer to the string: 'codepoint-str' does that.
(run! (comp println codepoint-str) (codepoints "bird 🐦"))

;; If you are not familiar with Unicode character processing on the JVM, you
;; might be wondering what problem this library solves. In a nutshell: in Java,
;; strings are represented in UTF-16 encoding, as sequences of char values. In
;; UTF-16, there is a 1-to-1 mapping of Unicode character to char only for basic
;; Unicode characters, but not for some characters of some writing systems of
;; the world, including, notably, emoji. Those need to be represented as two
;; chars, a pair of so-called 'surrogate' code units.
;;
;; Clojure inherits the char-based string handling of its host. For many
;; operations (map, filter, reverse, count) this behaviour is inadequate; hence
;; the need for a library like Couplet. A quick illustration follows.

;; Plain strings:
(seq "bird 🐦")
(count "bird 🐦")
(reverse "bird 🐦")
(apply str (reverse "bird 🐦"))  ; => "?? drib"!

;; Code points:
(count (seq (codepoints "bird 🐦")))
(reverse (codepoints "bird 🐦"))
(to-str (reverse (codepoints "bird 🐦")))  ; => "🐦 drib"


;; Using code point sequences

;; Couplet provides just the most basic functionality: turn a string into a
;; sequence of code points, and gather them up into a string again. That latter
;; part is covered by 'to-str', shown in the last example above. 'to-str' also
;; accepts a transducer that lets you transform any input. For example, we may
;; want to retain only supplementary code points.
(to-str (filter #(Character/isSupplementaryCodePoint %))
        (codepoints "bird 🐦"))

;; Any transformation done between obtaining the sequence of code point values
;; and coercing them into the desired shape is up to you: you can filter, map,
;; or otherwise transform the sequence like any other sequence.

;; By design, this library is small, and you are expected to make carefree use
;; of the rich Unicode APIs in the JDK such as java.lang.Character to create the
;; utilities that you need for your job.

(defn unicode-block [cp]
  (java.lang.Character$UnicodeBlock/of cp))

(defn emoji? [cp]
  (= java.lang.Character$UnicodeBlock/EMOTICONS (unicode-block cp)))

;; Then process to your heart's content: filter ...
(->> (codepoints "feeling🙃") (remove emoji?) to-str)

;; ... map ...
(map #(Character/getName %) (codepoints "🌹💃🌻"))

;; ... reduce.
(codepoint-str (reduce max -1 (codepoints "h🌝llo")))


;; Validation

;; A word about valid and invalid strings. It is legal for a string (or any
;; sequence of chars) to contain isolated surrogate code units, but that does
;; make it an ill-formed UTF-16 string. This library is lenient in the face of
;; ill-formed strings, exactly like the Java APIs. Stray surrogates are simply
;; passed through. This behaviour is efficient and transparent (all strings are
;; accepted: no exceptions).

;; Any validation you would like performed you can easily do yourself. For
;; example, you may simply want to detect ill-formed strings:

(defn surrogate? [cp]
  (and (Character/isBmpCodePoint cp) (Character/isSurrogate (char cp))))

(not-any? surrogate? (codepoints "abc\ud930def"))  ; U+D930 is a surrogate

;; Or you may choose to replace surrogates with the U+FFFD replacement
;; character. Such decisions are up to.

(def sanitize-isolated-surrogates
  (map #(if (surrogate? %) 0xFFFD %)))

(to-str sanitize-isolated-surrogates (codepoints "abc\ud930def"))


;; Fast reduction

;; The nice thing about having a custom type represent a sequence of code points
;; is that it allows that type to be extended to some core Clojure protocols to
;; achieve good performance.

;; First, CodePointSeq is reducible, that is, reducing a CodePointSeq is fast,
;; faster than iterating over a lazy seq of code points. Let's show the
;; performance difference for a large input string. First let us introduce a
;; sample string generator, and then use that to generate a large string.

(defn generate-text [n]
  (let [ascii-range (range 0 128)
        emoji-range (range 0x1F600 0x1F650)
        cp-strs (map codepoint-str (concat ascii-range emoji-range))]
    (apply str (repeatedly n #(rand-nth cp-strs)))))

(def large-string (generate-text 500000))  ; may take a few seconds

;; Now compare the two reductions. The second, reducible invocation is clearly
;; faster than the lazy one (up to 3x according to the benchmarks).
(time (into #{} (seq (codepoints large-string))))
(time (into #{} (codepoints large-string)))


;; Fast parallel folding

;; CodePointSeq also implements the CollFold protocol, making it possible to
;; do parallel processing of large strings.

;; Folding is done by using the fold function from the reducers namespace. If
;; you are not familiar with fold, I recommend reading its doc string before
;; studying the example.
(require '[clojure.core.reducers :as r])

;; In the following example we classify the code points of a random
;; one-million-character string and print the five most frequently occurring
;; Unicode blocks. We do this once with plain reduce, once with parallel fold.

(defn update-frequencies [m cp]
  (let [block (or (unicode-block cp) :none)]
    (update m block (fnil inc 0))))

(defn merge-frequencies
  ([] {})
  ([m1 m2] (merge-with + m1 m2)))

(let [one-million-cps (to-str (repeatedly 1e6 #(rand-int 0x1FFFF)))]
  (doseq [reduce-or-fold
          [(fn [s] (reduce update-frequencies {} s))
           (fn [s] (r/fold 8192 merge-frequencies update-frequencies s))]]
    (time (->> (codepoints one-million-cps)
               reduce-or-fold
               (sort-by val >)
               (take 5)
               (run! println)))))

;; On my machine, the speedup afforded by r/fold approaches 3x.


;; Spec

;; Couplet includes some support for code point specs. 'codepoint-in' lets you
;; spec a code point range. Thanks to the attached generator you can then easily
;; generate test data. For example, fruit and vegetables.

(require '[clojure.spec.alpha :as s]
         '[clojure.spec.gen.alpha :as gen])

(s/def ::fruit-n-veg (codepoint-in 0x1F345 0x1F353))
(s/valid? ::fruit-n-veg 0x1F351)  ; it's a peach

(gen/sample (s/gen ::fruit-n-veg))
(to-str (gen/sample (s/gen ::fruit-n-veg)))  ; a bunch of fruit and veg
