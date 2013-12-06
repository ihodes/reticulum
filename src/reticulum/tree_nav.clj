(ns reticulum.tree-nav
  (:require [plumbing.core  :as pp]
            [plumbing.graph :as graph]))


(defn zip [& colls] (apply map vector colls))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;; Generic tree functions 

(defn breadth-first-search
  "Breadth first search on a tree structure, returning a lazy sequence of trees
   for which pred? applied to the tree  is true. Recursively executed on sub 
   trees returned from applying children to the tree."
  [pred? children tree]
  (loop [acc '(), nodes (list tree)]
    (if (empty? nodes)
      acc
      (recur (lazy-cat acc (filter pred? nodes))
             (mapcat children nodes)))))

(defn path-to
  "Returns a seq of all the intermediate nodes on a path from the root node
   to the node which satisfies `pred?`. `tree` is the root node of the tree,
   and `children` returns all the children of a given node. Search is depth-
   first."
  ([pred? children tree] (path-to pred? children tree []))
  ([pred? children tree path]
     (let [path (conj (vec path) tree)]
       (if (pred? tree) path
           (->> (children tree)
                (map #(path-to pred? children % path))
                (remove nil?)
                first)))))

(def ^{:private true}
  -paths
  {:p1        (pp/fnk [p1? children tree] (path-to p1? children tree))
   :p2        (pp/fnk [p2? children tree] (path-to p2? children tree))
   :intersect (pp/fnk [p1 p2] (reduce (fn [i [p1 p2]] (if (= p1 p2) p1 i))
                                      nil
                                      (zip p1 p2)))
   :i-to-p1   (pp/fnk [p1 intersect] (drop-while #(not (= % intersect)) p1))
   :i-to-p2   (pp/fnk [p2 intersect] (drop-while #(not (= % intersect)) p2))})

(defn paths-between
  "Returns a (lazy) map containing the paths from the root of the tree to the node
   identified by p1? and by p2? under keys :p1 and :p2, as well as the point at which
   the two paths diverge, at :intersect, and the paths from the :intersect to those 
   nodes at :i-to-p1 and :i-to-p2."
  [p1? p2? children tree]
  ((graph/lazy-compile -paths) {:p1? p1? :p2? p2? :children children :tree tree}))
