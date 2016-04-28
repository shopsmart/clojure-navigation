# Mount a data structure and navigate it with filesystem-like commands.

* Each appropriate command pretty-prints the first 20 rows of the current data structure.
* Data structure listings are automatically paged so you're less likely to blow up your REPL
by inadvertently listing a huge data structure.
* At the same time, the current object pointed-to by the (pwd) is always available so you can
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

## Duck-typed Grep for deeply recursively nested data structures.

Generally:

```
    (grep matcher root-object)
```
Recursively applies matcher to *all* children of root-object.  When a match is found, the container
of the matched object is returned.

The matcher itself can be any type.  If it is a regular expression Pattern, it is matched against
strings or the output of (.toString obj) Strings match any substring of (.toString object).
All other objects match using (= matcher obj).

Example:

```
    => (grep :diffed (current))
    [[:diffed {}] [:diffed {}]]
```
In this case, grep searched a data structure that reports differences between two related objects.
This run determined that there were no differences found under (current).


## A pipe operator that can map, mapcat, and reduce a collection in Unix-style.

```clojure
    (| (range 50) inc #(/ % 2) +)
```

## Behavior injection

(Experimental module)

Inject behavior before/after/around all forms in a do-style block or thread-last
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

### Usage

These utilities are designed mainly to enhance REPL usage, so we suggest adding them to your
.lein/profiles.clj in the :user or :dev profile or equivalent for your build tool.

### Leiningen coordinates

One might always want these utilities available, even at runtime.  In that case, adding
them to your :user profile would make sense.  To do that, merge the following into your
:user map in your profiles.clj file.

```clojure
:user {:repositories [["jitpack" "https://jitpack.io"]]

       :dependencies [[com.github.shopsmart/clojure-navigation "version"]]}
```

where "version" currently is "[![Release](http://jitpack.io/v/com.github.shopsmart/clojure-navigation.svg)](https://jitpack.io/#shopsmart/clojure-navigation)".

### Maven coordinates

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
	<name>Jitpack repo</name>
	<url>https://jitpack.io</url>
  </repository>
</repositories>
```

* GroupId: com.github.shopsmart
* ArtifactId: clojure-navigation
* Version: [![Release](http://jitpack.io/v/com.github.shopsmart/clojure-navigation.svg)](https://jitpack.io/#shopsmart/clojure-navigation)


## License

Copyright © 2015 by ShopSmart, LLC.  Licensed under the Eclipse Public License v1.0.

## Authors

David Orme
