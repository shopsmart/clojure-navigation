(ns bradsdeals.pipe
  "A Unix-style pipe operator and recursive grep command.  Pipe operates on a single
container's elements.  Grep recursively searches nested data structures.

Also includes xreduce, a version of reduce that returns a transducer function."
  (:require [clojure.zip :as zip]
            [clojure.core.reducers :as r]
            [bradsdeals.tree-visit :as v])
  (:import [java.util Map Map$Entry])
  (:gen-class))


(defn- arity
 "Returns the maximum parameter count of each invoke method found by refletion
  on the input instance. The returned value can be then interpreted as the arity
  of the input function. The count does NOT detect variadic functions."
  [f]
  (let [invokes (filter #(= "invoke" (.getName %1)) (.getDeclaredMethods (class f)))]
    (apply max (map #(alength (.getParameterTypes %1)) invokes))))


(defn- mapcatfn [fn1]
  (mapcat (fn [x] (let [r (fn1 x)]
                    (cond
                      (or (map? r) (sequential? r)) r
                      (nil? r) []
                      :else [r])))))


(defn xreduce
  "A version of reduce that returns a transducer function.  There are two arities
of this function, one accepting a single reducer function and one that accepts
a reducer function and an initial value.  Just like the transducer-producing
arities in the standard library (e.g.: map, mapcat), this function does not
include an arity accepting an input collection.

Semantics of the transducer function reduce are the same as the semantics of a
regular reduce."
  ([f initial-value]
   (fn [reducing-fn]
     (let [prev (volatile! initial-value)]
       (fn
         ([] (reducing-fn))
         ([result] (if (not= @prev ::none)
                     (do (let [final-result (reducing-fn result @prev)]
                         (vreset! prev ::none)
                         final-result))
                     (reducing-fn result)))
         ([result input]
          (let [prior @prev]
            (if (= prior ::none)
              (do (vreset! prev input)
                  result)
              (let [next (f prior input)]
                (if (reduced? next)
                  (do (vreset! prev ::none)
                      (reducing-fn result @next))
                  (do (vreset! prev next)
                      result))))))))))
  ([f]
   (xreduce f ::none)))


(defn- fn->transducer [fn]
  (cond
    (vector? fn) (xreduce (first fn) (second fn))
    (= (arity fn) 1) (mapcatfn fn)
    (= (arity fn) 2) (xreduce fn)
    :else (throw (IllegalArgumentException. (str "Unexpected arity: " (arity fn))))))


(defn- fns->transducer
  "Compute a transducer function composing fns according to Unix pipe semantics"
  [fns]
  (let [transducers (map fn->transducer fns)]
    (apply comp transducers)))


(defn |
 "Unix pipeline semantics for Clojure.  Input can be any container or a simple type.
For a container, the output will be the same type as the input.  If the input is a
simple type, it will be wrapped in a vector before being processed and the result
will be a vector.  Supported container types include Map, List, Vector, and Set.

fns may include any of the following:

* An arity 1 function is treated as a mapcat function with the following relaxed
rules:  If it returns a value of a simple type, the value is appended to the output.
A nil result is considered an empty result.  Otherwise, mapcat semantics are followed.
e.g.: if you want the result to be a collection of collections, the sub-collection
must first be wrapped in another collection so the sub-collection itself will be
concatinated onto the result.

* An arity 2 function is treated as a reducing function where the initial two
collection elements specify the initial two elements in the reduction.  The reducer
supports the 'reduced' function in the standard library so that a single input
collection can produce multiple reduced outputs.

* A vector containing an arity 2 function and a second value treats the function as
a reducer and the second value as the initial value in the reduction.

The input is processed through fns, a single element at a time, without creating
intermediate collections, in the order in which fns are specified."
 [input & fns]
 (let [input (if (or (vector? input) (sequential? input)) input [input])
       composed-fns (fns->transducer fns)]
   (sequence composed-fns input)))


(defn supported-coll? [node]
  (or (map? node) (vector? node) (list? node) (set? node) (seq? node)))

(defn parent-container [loc]
  (let [up (zip/up loc)
        parent (if up (zip/node up))]
    (cond
      (nil? parent) (zip/node loc)
      (supported-coll? parent) parent
      :else (parent-container (zip/up loc)))))

(defn depth [loc]
  (if (nil? (zip/up loc))
    0
    (+ (if (instance? Map$Entry (zip/node loc))
         0
         1)
       (depth (zip/up loc)))))

(defn- index [loc]
  (count (zip/lefts loc)))

(defn update-breadcrumb [old-path loc]
  (let [parent (parent-container loc)
        new-depth (depth loc)
        new-index (index loc)
        new-node (zip/node loc)]
    (if (or (instance? Map$Entry new-node) (nil? (zip/up loc)))
      old-path
      (if (instance? Map$Entry parent)
        (if (not (supported-coll? new-node))
          (conj (vec (take (- new-depth 1) old-path)) new-node)
          old-path)
        (conj (vec (take (- new-depth 1) old-path)) new-index)))))


(defn- grep-tree-visitor [pattern]
  (let [breadcrumb (atom [])]
    (fn [node state loc]
      (swap! breadcrumb update-breadcrumb loc)
      (if-not (or (supported-coll? node) (nil? node))
        (if (instance? java.util.regex.Pattern pattern)
          (if (re-matches pattern (.toString node))
            {:state (conj state [@breadcrumb (parent-container loc)])})
          (if (string? pattern)
            (if (.contains (.toString node) pattern)
              {:state (conj state [@breadcrumb (parent-container loc)])})
            (if (= pattern node)
              {:state (conj state [@breadcrumb (parent-container loc)])})))))))


(defn grep
  "Recursively grep the contents of node for elements matching pattern.  Pattern can be
any type.  If it is a regular expression, it is matched against (.toString obj).  String
patterns match any substring of (.toString obj).  All other types must match literally.
If node is omitted, returns an arity-1 function with pattern bound to the specified value,
and the remaining parameter being the start node.

Returns a vector of the containers of the matched values.  If multiple values in a
container match, the same container will be returned multiple time."
  ([pattern node]
    (:state
      (v/tree-visitor (v/tree-zipper node) [] [(grep-tree-visitor pattern)])))
  ([pattern]
    (partial grep pattern)))
