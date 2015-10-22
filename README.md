# clojure-navigation

Clojure-navigation contains utilities for navigating data and/or code.  For example:

* Mount a data structure and navigate it with filesystem-like commands.

    (mount data-structure)
    (cd :child-node 1 :another-child ...)
    (ls)
    (current) - return the current object
    (current :sub1 :sub2 ...) - return the object referenced by :sub1 and :sub2 from (current)

* A pipe operator that can map, mapcat, and reduce a collection in Unix-style.

    (| (range 50) inc #(/ % 2) +)

* Duck-typed Grep for deeply recursively nested data structures.

The matcher can be any type.  If it is a regular expression Pattern, it is matched against
strings or the output of (.toString obj) Strings match any substring of the target object.
All other objects match using (= matcher obj).

* Inject behavior before/after/around all forms in a do-style block or thread-last
macro form (experimental).

For example:

    (inject logging (form1) (form2) ...)

Logs each form as it is executed.  If a form takes longer than 1/2 second, logs the elapsed
time as well.

Or:

    (fns (form1) (form2) ...)

Returns a vector containing all forms converted into 0-arg functions.  These functions then
can be executed during a map, mapcat, or reduce operation and their results stored, further
processed, logged, etc.

## Usage

src/bradsdeals/nav.clj - Adds Unix-like navigation and browsing for nested Clojure
data structures.  See the file itself for more information.

src/bradsdeals/util.clj - Dependencies of nav.clj and experimental Clojure code.

## License

Copyright © 2015 by ShopSmart, LLC.  Licensed under the Eclipse Public License v1.0.

## Authors

David Orme

