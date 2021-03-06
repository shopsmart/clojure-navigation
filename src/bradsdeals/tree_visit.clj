(ns bradsdeals.tree-visit
  "Visitor pattern implementation for arbitrary Clojure data structures."
  (:require [clojure.zip :as zip]
            [clojure.set :as set])
  (:import [clojure.lang IPersistentSet IPersistentList IPersistentVector IPersistentMap ISeq])
  (:gen-class))

;;---------------------------------------------------------------------------------
;; Thanks to: http://www.ibm.com/developerworks/library/j-treevisit/

(defmulti tree-branch? class)
(defmethod tree-branch? :default [_] false)
(defmethod tree-branch? IPersistentVector [v] true)
(defmethod tree-branch? IPersistentMap [m] true)
(defmethod tree-branch? IPersistentList [l] true)
(defmethod tree-branch? IPersistentSet [s] true)
(defmethod tree-branch? ISeq [s] true)

(defmulti tree-children class)
(defmethod tree-children IPersistentVector [v] v)
(defmethod tree-children IPersistentMap [m] (seq m))
(defmethod tree-children IPersistentList [l] l)
(defmethod tree-children IPersistentSet [s] (seq s))
(defmethod tree-children ISeq [s] s)

(defmulti tree-make-node (fn [node children] (class node)))
(defmethod tree-make-node IPersistentVector [v children] (vec children))
(defmethod tree-make-node IPersistentMap [m children]
           (apply hash-map (apply concat children)))
(defmethod tree-make-node IPersistentList [_ children] children)
(defmethod tree-make-node IPersistentSet [_ children] (apply set children))
(defmethod tree-make-node ISeq [node children] (apply list children))
(prefer-method tree-make-node IPersistentList ISeq)

(defn tree-zipper [node]
  (zip/zipper tree-branch? tree-children tree-make-node node))

(defn visit-node
  [start-node start-state visitors loc]
  (loop [node start-node
         state start-state
         [first-visitor & rest-visitors] visitors]
    (let [context (merge {:node node, :state state, :stop false, :next false}
                         (first-visitor node state loc))
          {new-node :node
           new-state :state
           :keys (stop next)} context]
      (if (or next stop (nil? rest-visitors))
        {:node new-node, :state new-state, :stop stop}
        (recur new-node new-state rest-visitors)))))

(defn tree-visitor
  ([zipper visitors]
     (tree-visitor zipper nil visitors))
  ([zipper initial-state visitors]
     (loop [loc zipper
            state initial-state]
       (let [context (visit-node (zip/node loc) state visitors loc)
             new-node (:node context)
             new-state (:state context)
             stop (:stop context)
             new-loc (if (= new-node (zip/node loc))
                       loc
                       (zip/replace loc new-node))
             next-loc (zip/next new-loc)]
         (if (or (zip/end? next-loc) (= stop true))
           {:node (zip/root new-loc) :state new-state}
           (recur next-loc new-state))))))

