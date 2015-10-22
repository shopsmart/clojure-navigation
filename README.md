# clojure-navigation

Clojure-navigation contains utilities for navigating data and/or code.  For example:

(mount data-structure)
(cd :child-node 1 :another-child ...)
(ls)
(current) - return the current object
(current :sub1 :sub2 ...) - return the object referenced by :sub1 and :sub2 from (current)

A pipe operator that can map, mapcat, and reduce a collection in Unix-style.

Duck-typed Grep for deeply recursively nested data structures.

* The matcher can be any type.  If it is a regular expression Pattern, it is matched against
strings or the output of (.toString obj) Strings match any substring of the target object.
All other objects match using (= matcher obj).

## Usage

src/bradsdeals/nav.clj - Adds Unix-like navigation and browsing for nested Clojure
data structures.  See the file itself for more information.

src/bradsdeals/util.clj - Dependencies of nav.clj and experimental Clojure code.

## License

Copyright © 2015 by ShopSmart, LLC.

