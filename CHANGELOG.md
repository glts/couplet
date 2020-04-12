# Couplet changelog

## 0.2.1 (2020-04-12)

*   Bump dependency versions.
*   Update use of deprecated test.check functions in tests.

## 0.2.0 (2019-05-03)

*   The value returned by `couplet.core/codepoints` now implements the
    `clojure.core.reducers/CollFold` protocol directly.
*   Add `couplet.core/surrogate?`.

## 0.1.2 (2018-09-17)

*   Assert that the argument to `couplet.core/codepoints` is of type
    `CharSequence`.
*   Add a `deps.edn` file.
*   Remove the spec for `couplet.core/codepoint-str`.

## 0.1.1 (2017-12-15)

*   Improve reduce performance.
*   Bump Clojure version to 1.9.0.

## 0.1.0 (2017-10-19)

Initial release.
