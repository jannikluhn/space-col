(ns space-col.core
  (:require [clojure.math.numeric-tower :as math]
            [clojure.set :as set]
            [kdtree]
            [space-col.euclid :as eu]
            [same :refer [ish?]])
  (:import [kdtree Node]))

;
; kdtree extensions
;
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

;
; space colonization algorithm
;
(defn influenced-node
  "Returns the vein node that is influenced by the given source node, if any.

  `di` denotes the influence distance."
  [di vein-kdt source]
  (let [nn (:point (kdtree/nearest-neighbor vein-kdt source))]
    (if (<= (eu/dist nn source) di)
      nn)))

(defn invert-map-aggregating
  "Inverts a map such that values become keys and keys are merged in sets."
  [m]
  (reduce (fn [m [k v]]
            (update m v #(conj (set %) k))) {} m))

(defn influence-mapping
  "Returns a mapping from vein nodes to the set of source nodes they are influenced by.

  `di` denotes the influence distance."
  [di vein-kdt sources]
  (->> sources
       (map (partial influenced-node di vein-kdt))
       (map vector sources)
       (remove (comp nil? second))
       (apply concat)
       (apply hash-map)
       (invert-map-aggregating)))

(defn influence-dir
  "Return the direction to which a node is pulled towards by a set of sources.

  The result is a normalized vector."
  [sources node]
  (let [influence-sum (->> sources
                           (map #(eu/vec- % node))
                           (map eu/norm)
                           (remove nil?)
                           (apply eu/vec+))]
    (eu/norm influence-sum)))

(defn branchlet-tip
  "Returns the tip of a new branchlet, if any.

  The branchlet is given by its root and its direction `dir`. `ds` denotes the length of the
  branchlet"
  [ds dir branchlet-root]
  (if-not (nil? dir)
    (eu/vec+ branchlet-root (eu/vec* dir ds))))

(defn branchlets
  "Returns the branchlets defined by an influence mapping.

  `ds` denotes the steps distance."
  [ds im]
  (let [[start-nodes influence-sets] [(keys im) (vals im)]
        dirs (map influence-dir influence-sets start-nodes)
        tips (map (partial branchlet-tip ds) dirs start-nodes)
        branchlets (map vector start-nodes tips)]
    (filter (comp (complement nil?) second) branchlets)))

(defn victims
  "Returns the set of source nodes in the kdtree that are too close to the given vein node.

  `dk` denotes the kill distance."
  [dk source-kdt vein-node]
  (if-not (nil? vein-node)
    (set (sphere-search source-kdt vein-node dk))
    #{}))

(defn overlapping-branchlet?
  "Returns true if start and end point of a branchlet overlaps with existing vein nodes."
  [vein-kdt branchlet]
  (->> branchlet
       (map (partial kdtree/nearest-neighbor vein-kdt))
       (map :point)
       (map ish? branchlet)
       (every? true?)))

;
; External API
;
(defn init
  "Creates an initial state."
  [roots sources]
  {:vein-kdt (kdtree/build-tree roots)
   :source-kdt (kdtree/build-tree sources)})

(defn stopped?
  "Returns true if the algorithm has stopped, otherwise false."
  [{:keys [branchlets victims]}]
  (and (some? branchlets) (some? victims) (empty? branchlets) (empty? victims)))

(defn step
  "Advances the state by one step."
  [{:as params :keys [ds di dk]} {:as state :keys [vein-kdt source-kdt]}]
  (let [im (influence-mapping di vein-kdt (iterate-kdtree source-kdt))
        bs-with-overlap (branchlets ds im)
        bs (filter (complement (partial overlapping-branchlet? vein-kdt)) bs-with-overlap)
        new-nodes (map second bs)
        vs (->> new-nodes
                (map (partial victims dk source-kdt))
                (apply set/union))]
    {:vein-kdt (reduce kdtree/insert vein-kdt new-nodes)
     :source-kdt (reduce kdtree/delete source-kdt vs)
     :branchlets bs
     :victims vs}))

(defn steps
  "Returns a lazy sequence of all intermediate states until completion."
  [params state]
  (take-while (complement stopped?) (iterate (partial step params) state)))
