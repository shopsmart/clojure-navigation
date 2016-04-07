(ns bradsdeals.nav
  "A set of *nix-like utilities making it easier to navigate Clojure
data structures from a REPL.

Synopsis:

(mount data-structure)
(pwd)
(current) Returns the current object to the REPL or in expressions.
(current :sub1 :sub2 ...) Return subobject pointed to by :sub1, :sub2, ...
(ls) Display the contents of (current), paged to 20 at a time.
(ls 2) Display the second page of (current), etc.
(cd :sub1 :sub2 ...) Change to subobjects via map keys
(cd 0 3 ...) Change to subobjects of sequential objects that support (nth)
(cd :sub1 3 :sub2 ...) cd forms may be mixed.
(cd \"..\") Go up a level in the object hierararchy.
(cd) Return to the top level (home directory)"
  (:require [clojure.pprint :as pp :refer [pprint]]
            [bradsdeals.util.errors :as err]
            [clojure.string :as s])
  (:gen-class))


(defrecord DSCursor [root pwd current partitions])


(defn- save-state [state]
  (intern *ns* (symbol "_dscursor") state))


(defn- find-cursor []
  (if-let [dscursor (resolve '_dscursor)]
    (var-get dscursor)))


(defn- descend [container key] (err/try* (container key) (key container)))


(defn pwd
  "Print the current object's name and a vector describing the path descended from the object's root."
  [] (if-let [cursor (find-cursor)]
               (:pwd cursor)))

(defn current
  "Return the current Clojure object pointed to by the pwd and ls functions.  Optionally, 'current'
can accept arguments in the same format as the 'cd' function which will navigate down the object
hierarchy from the current object and return the object pointed to after navigating through each
subobject key."
  [& subobject-keys]
  (if-let [cursor (find-cursor)]
    (let [cur (:current cursor)]
      (reduce (fn [last next] (descend last next)) cur subobject-keys))))


(defn- current->subs
  [f & subobject-keys]
  (if (map? (current))
    (into {} (map (fn [[k v]] [k (f (reduce (fn [last next] (descend last next)) v subobject-keys))])
                  (current)))
    "Current object must be a map."))


;; ls
(defn- format-subthing [name subthing & row-num]
  (format "%s %-40s %s[%s]: %-40s\n"
          (str (first row-num))
          (str name)
          (if subthing (.getName (type subthing)) subthing)
          (str (err/try* (count subthing) subthing))
          (apply str (take 80 (pp/write subthing :stream nil :length 2 :level 5 :right-margin 400)))))


(defn- format-element [subthing & row-num]
  (format "%s %-40s %s[%s]\n"
          (str (first row-num))
          (apply str (take 40 (pp/write subthing :stream nil :length 2 :level 5 :right-margin 400)))
          (str (err/try* (.getName (type subthing)) subthing))
          (str (err/try* (count subthing) subthing))))


(defn- ls-map [thing]
  (map (fn [key]
         (let [subthing (descend thing key)]
           (format-subthing key subthing)))
       (keys thing)))


(defn ls-seq [thing]
  (let [pairs (map vector (range (count thing)) thing)]
    (map (fn [[rownum element]]
           (cond
             (map? element) (format-element element rownum)
             (sequential? element) (format-element element rownum)
             :else (format-subthing element element rownum)))
         pairs)))


(defn ls- [thing]
  (cond
    (map? thing) (ls-map thing)
    (sequential? thing) (ls-seq thing)
    :else [(format " %-40s %s[%s]\n" (str thing) (.getName (type thing)) (err/try* (count thing) thing))]))


(defn ls
  "List the contents of the current object.  If the current object contains more than 20 elements, the
listing is automatically paged.  In that case 'ls' by itself lists the initial page.  'ls 2' the second
page, 'ls 3' the third, and so on."
  [& page]
  (if-let [cursor (find-cursor)]
    (let [display-page (if (first page) (first page) 1)
          partitions (:partitions cursor)
          pages (count partitions)
          total (if (= pages 1) (count (first partitions)) (reduce #(+ %1 (count %2)) 0 partitions))]
      (pprint (:pwd cursor))
      (print " ")
      (apply print (nth partitions (dec display-page)))
      (println "Page" display-page "of" pages "; total elements: " total))))


(defmacro mount
  "Mount an object so that it can be interacted with using Unix-style commands like 'ls', 'cd', etc.
  If 'root' is present, it is the root object to mount, otherwise, lists all the vars in the current
  namespace."
  [& root]
  (if (> 0 (count root))
    `(named-mount ~root (keyword (str (quote ~@root))))
    `(ns-interns *ns*)))


(defn named-mount
  "This function is called by the mount macro; use that instead."
  ([root path thing]
   (let [contents (ls- thing)
         pages (partition-all 20 contents)
         total (count contents)]
     (save-state (DSCursor. root path thing pages))))
  ([root root-name]
    (if root
      (do (named-mount root {root-name []} root)
      (ls)))))


(defn cd
  "Change the current location to another object.  If the current object is a map, the parameter value
must be the key of the entry to which to change.  If the current object is a sequence, the parameter value
must be an integer identifying the object by its offset in the sequence.  To change to the current object's
parent, pass the string \"..\".

Multiple parameters may be supplied in order to change the current location by multiple levels at once."
  [& where]
  (if-let [cursor (find-cursor)]
    (do
      (let [root (:root cursor)
            nextkey (first where)
            current (:current cursor)
            pwd-map (:pwd cursor)
            pwd-key (first (keys pwd-map))
            pwd (pwd-map pwd-key)]
        (cond
          (= (count where) 0) (do (named-mount root {pwd-key []} root) (ls))
          (and (= ".." nextkey) (> (count pwd) 0)) (do (named-mount root {pwd-key []} root) (doseq [k (pop pwd)] (cd k)))
          (map? current) (do (named-mount (:root cursor) {pwd-key (conj pwd nextkey)} (descend current nextkey)) (ls))
          (sequential? current) (do (named-mount (:root cursor) {pwd-key (conj pwd nextkey)} (nth current nextkey)) (ls))))
      (if (> (count (rest where)) 0)
        (recur (rest where))))))
