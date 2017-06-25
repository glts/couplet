# Couplet

Couplet is a small library that provides support for working with Unicode
characters or ‘code points’ in Clojure.

The distinguishing property of this library is the type that represents a
sequence of code points: that type is efficiently seqable and reducible, and
also supports parallel fold via fork/join.

This library targets Clojure on the JVM.

[![Clojars Project](https://img.shields.io/clojars/v/ch.gluet/couplet.svg)](https://clojars.org/ch.gluet/couplet)
[![Build Status](https://travis-ci.org/glts/couplet.svg?branch=master)](https://travis-ci.org/glts/couplet)

## Requirements

*   Clojure 1.9
*   Java 8

## Usage

    (require '[couplet.core :as cp])

    (cp/codepoints "b🐝e🌻e")
    ; => #couplet.core.CodePointSeq["b🐝e🌻e"]

    (seq (cp/codepoints "b🐝e🌻e"))
    ; => (98 128029 101 127803 101)

    (cp/to-str (take-nth 2 (cp/codepoints "b🐝e🌻e")))
    ; => "bee"

## Documentation

*   [API documentation](https://glts.github.io/couplet/couplet.core.html)
*   [Walkthrough](https://github.com/glts/couplet/blob/master/example/walkthrough.clj)

## Licence

Copyright © 2017 David Bürgin

Distributed under the Eclipse Public License either version 1.0 or (at your
option) any later version.
