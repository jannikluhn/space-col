(ns space-col.utils
  (:require [kdtree]
            [space-col.euclid :as eu]))

(defn sphere-search
  "Finds all points whose distance to `center` is less or equal than `radius`."
  [tree center radius]
  (let [box (map #(vector (- % radius) (+ % radius)) center)
        in-box (kdtree/interval-search tree box)
        in-sphere (filter #(<= (eu/dist % center) radius) in-box)]
    in-sphere))

(defn iterate-kdtree
  "Returns a lazy sequence of the points stored in the tree."
  [tree]
  (->> tree
       (tree-seq (partial instance? kdtree.Node) #(vector (:left %) (:right %)))
       (remove nil?)
       (map :value)
       (map vec)))

(defn invert-map-aggregating
  "Inverts a map such that values become keys and keys are merged in sets."
  [m]
  (reduce (fn [m [k v]]
            (update m v #(conj (set %) k))) {} m))
