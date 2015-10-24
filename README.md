# clojure-navigation

Clojure-navigation contains utilities for navigating data and/or code.  For example:

* Mount a data structure and navigate it with filesystem-like commands.  Each appropriate command
pretty-prints the first 20 rows of the current data structure.  Data structure listings are automatically
paged so you're less likely to blow up your REPL by inadvertently listing a huge data structure.
At the same time, the current object pointed-to by the (pwd) is always available so you can
map, mapcat, reduce, and grep the actual objects to your heart's content.

```clojure
    (mount data-structure)
    (cd :child-node 1 :another-child ...)
    (cd "..")
    (ls)
    (pwd)
    (current) ; return the current object
    (current :sub1 :sub2 ...) ; return the object referenced by :sub1 and :sub2 from (current)
```

* Duck-typed Grep for deeply recursively nested data structures.

Generally:

```
    (grep matcher root-object)
```
The matcher can be any type.  If it is a regular expression Pattern, it is matched against
strings or the output of (.toString obj) Strings match any substring of (.toString object).
All other objects match using (= matcher obj).

Example:

```
    => (grep :diffed (current))
    [[:diffed {}] [:diffed {}]]
```

* A pipe operator that can map, mapcat, and reduce a collection in Unix-style.

```clojure
    (| (range 50) inc #(/ % 2) +)
```

* Inject behavior before/after/around all forms in a do-style block or thread-last
macro form (experimental).

For example:

```clojure
    (inject logging (form1) (form2) ...)
```

Logs each form as it is executed.  If a form takes longer than 1/2 second, logs the elapsed
time as well.

Or:

```
    (fns (form1) (form2) ...)
```

Returns a vector containing all forms converted into 0-arg functions.  These functions then
can be executed during a map, mapcat, or reduce operation and their results stored, further
processed, logged, etc.

## Usage

Open project.clj and change the line that reads:

```
    :repositories [["snapshots" {:url "file:/Users/dorme/.m2/repository/"
```

to point to your local Maven repository

Build using

```
    lein jar
    lein deploy snapshots
```

Then depend on it in Leiningen using:

```
    [com.bradsdeals/clojure-navigation "1.0.0-SNAPSHOT"]
```

TODO: Add clojars information once we have an official build.

## License

Copyright © 2015 by ShopSmart, LLC.  Licensed under the Eclipse Public License v1.0.

## Authors

David Orme

