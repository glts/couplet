# Couplet

Couplet is a small library that provides support for working with Unicode
characters or ‘code points’ in Clojure.

The distinguishing feature of this library is the type that represents a
sequence of code points: that type is efficiently seqable and reducible, and
also supports parallel fold via fork/join.

This library targets Clojure on the JVM.

[![Clojars Project](https://img.shields.io/clojars/v/ch.gluet/couplet.svg)](https://clojars.org/ch.gluet/couplet)
[![Build Status](https://travis-ci.org/glts/couplet.svg?branch=master)](https://travis-ci.org/glts/couplet)

## Requirements

*   Clojure 1.9
*   Java 8

## Dependency information

deps.edn:

```clojure
ch.gluet/couplet {:mvn/version "0.2.0"}
```

Leiningen/Boot:

```clojure
[ch.gluet/couplet "0.2.0"]
```

## Documentation

*   [API documentation](https://glts.github.io/couplet/couplet.core.html)

## Usage

Require the core namespace, preferably aliasing it as `cp`.

```clojure
(require '[couplet.core :as cp])
```

The central function in this library is `cp/codepoints`. When passed a string or
other `CharSequence`, it returns a seqable/reducible succession of the Unicode
code points contained in the string.

Code points are simply the platform integers (same as UTF-32 code units).

```clojure
(seq (cp/codepoints "bird🐦"))
; => (98 105 114 100 128038)
```

The value proposition of `cp/codepoints` is the capability of treating strings
as sequences of _Unicode characters_, as opposed to the awkward default
treatment of such things in Clojure as sequences of `char`s, that is UTF-16 code
units.

An example showing counting and (naive) reversal illustrates this difference:

```clojure
(count (seq "bird🐦"))
; => 6
(count (seq (cp/codepoints "bird🐦")))
; => 5

(apply str (reverse "bird🐦"))
; => "??drib"
(cp/to-str (reverse (cp/codepoints "bird🐦")))
; => "🐦drib"
```

A sequence of code points can be turned back into a string with `cp/to-str`.
This function can take a transducer as the first argument to apply an additional
transformation to the inputs.

```clojure
(def bee
  (into [] (cp/codepoints "b🐝e🌻e")))

(cp/to-str bee)
; => "b🐝e🌻e"

(cp/to-str (take-nth 2) bee)
; => "bee"
```

Calling `cp/codepoints` without arguments returns a transducer that converts
`char` inputs to code points. This transducer is useful when dealing with values
that do not implement `CharSequence`, such as Java arrays.

```clojure
(into [] (cp/codepoints) (char-array "bird🐦"))
; => [98 105 114 100 128038]
```

The function to turn an opaque code point integer back into readable string form
is called `cp/codepoint-str`.

```clojure
(run! (comp println cp/codepoint-str) (cp/codepoints "bird🐦"))
; b
; i
; r
; d
; 🐦
```

String inputs are always handled in a lenient, non-failing fashion. Invalid data
such as isolated (unpaired) surrogates pass through untouched. Where desired,
validation and sanitization can be implemented like any other transformation
using existing general transformation functions.

```clojure
(not-any? cp/surrogate? (cp/codepoints "broken\ud930"))
; => false, U+D930 is an isolated surrogate

(def sanitize-surrogates
  (map #(if (cp/surrogate? %) 0xFFFD %)))

(cp/to-str sanitize-surrogates (cp/codepoints "broken\ud930"))
; => "broken�"
```

In addition to supporting efficient reduction, code point sequences support
parallel processing via `clojure.core.reducers/fold`.

For example, we can calculate the most frequently occurring Unicode blocks in
some large input string. With fold, the work is transparently divided into tasks
that are then processed in parallel. In the ideal case, this should improve
performance by a factor proportional to the number of processors.

```clojure
(require '[clojure.core.reducers :as r])

(defn update-frequencies [m cp]
  (update m (java.lang.Character$UnicodeBlock/of (int cp)) (fnil inc 0)))

(defn merge-frequencies
  ([] {})
  ([m1 m2] (merge-with + m1 m2)))

(let [s (cp/to-str (repeatedly 1e6 #(rand-int 0x1FFFF)))]
  (->> (cp/codepoints s)
       (r/fold 10000 merge-frequencies update-frequencies)
       (sort-by val >)
       (take 10)))
```

Specs for code points are covered by the predicate `cp/codepoint?` and the
corresponding spec `::cp/codepoint`. The macro `cp/codepoint-in` can be used
to spec a code point range.

Thanks to the attached generator you can generate test data easily.

```clojure
(require '[clojure.spec.alpha :as s]
         '[clojure.spec.gen.alpha :as gen])

(s/def ::fruit-n-veg (cp/codepoint-in 0x1F345 0x1F353))

(s/valid? ::fruit-n-veg 0x1F351)
; => true, it’s a peach

(cp/to-str (gen/sample (s/gen ::fruit-n-veg)))
; => "🍍🍍🍆🍅🍎🍓🍊🍌🍍🍓"
```

Code point literals are occasionally useful, for example when attempting to
write human-readable `cp/codepoint-in` specs. Register a tagged literal of your
choice to enable code point literals; the following snippet shows how.

```clojure
(defn read-codepoint [s]
  (first (cp/codepoints s)))

(set! *data-readers* (assoc *data-readers* 'cp #'read-codepoint))

(s/valid? (cp/codepoint-in #cp "🍅", #cp "🍓")
          #cp "🍑")
; => true
```

Refer to the
[`java.lang.Character`](https://docs.oracle.com/javase/8/docs/api/java/lang/Character.html)
Javadoc for JDK APIs that can be fruitfully combined with the functionality
provided in this library.

## Design goals

*   *small*: provide basic building blocks for working with Unicode characters,
    not more
*   *efficient*: as performant as reasonably possible in Clojure on the JVM
*   *transparent*: allow processing any string, no well-formedness requirement
    imposed, no exceptions thrown nor mangling done on ill-formed UTF-16 input

## Related work

There are other solutions for the same problem, though perhaps written with
different goals in mind.

*   https://github.com/richhickey/clojure-contrib/blob/master/src/main/clojure/clojure/contrib/string.clj
*   https://github.com/daveyarwood/djy
*   https://lambdaisland.com/blog/12-06-2017-clojure-gotchas-surrogate-pairs

Check out [ICU](http://site.icu-project.org/) for an extensive, mature Java
library for Unicode.

## Performance

Run the benchmarks with

```
lein jmh '{:type :quick, :format :table}'
```

The following is a short summary of the findings.

Broadly speaking, processing strings using code points instead of `char`s has no
negative impact on performance. On the contrary, the performance achieved here
compares favourably with that of Clojure’s own `char`-based string processing.

*   Reduce is faster than processing a lazy seq of code points by a factor of 3.
*   Parallel fold can be faster than reduce by a factor proportional to the
    number of cores.
*   Compared with Clojure strings, performance differences range from on par
    (reducing code points versus reducing a string) to faster by a factor of 3
    (`cp/to-str` versus `apply str`) to faster by a factor of 5 (lazy seq of
    code points versus lazy seq of `char`s).

Strings support fast random access – code point seqs do not. For efficient
lookup of code points by index consider a `vector-of :int` or Java array of
`int`.

## Licence

Copyright © 2019 David Bürgin

Distributed under the Eclipse Public License either version 1.0 or (at your
option) any later version.
