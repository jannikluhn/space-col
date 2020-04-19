(ns space-col.core
  (:require [clojure.math.numeric-tower :as math]
            [clojure.set :as set]
            [kdtree]
            [space-col.euclid :as eu])
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
  "Returns the vein node that is influenced by the given source node if any."
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
  "Returns a mapping from vein nodes to the set of source nodes they are influenced by."
  [di vein-kdt sources]
  (->> sources
       (map (partial influenced-node di vein-kdt))
       (map vector sources)
       (remove (comp nil? second))
       (apply concat)
       (apply hash-map)
       (invert-map-aggregating)))

(defn influence-dir [sources node]
  "Return the direction to which a node is pulled towards by a set of sources.
  
  The result is a normalized vector."
  (let [influence-sum (->> sources
                           (map #(eu/vec- % node))
                           (map eu/norm)
                           (remove nil?)
                           (apply eu/vec+))]
    (eu/norm influence-sum)))

(defn branchlet-tip
  "Returns the tip of a new branchlet, if any."
  [ds dir branchlet-root]
  (if-not (nil? dir)
    (eu/vec+ branchlet-root (eu/vec* dir ds))))

(defn branchlets
  "Returns the branchlets defined by an influence mapping."
  [ds im]
  (map (fn [[n s]] [n (branchlet-tip ds (influence-dir s n) n)])
       im))

(defn victims
  "Returns the set of source nodes in the kdtree that are too close to the given vein node.
  
  dk denotes the kill distance."
  [dk source-kdt vein-node]
  (if-not (nil? vein-node)
    (set (sphere-search source-kdt vein-node dk))
    #{}))

(defn init
  "Initializes a new state to start space colonization."
  [roots sources]
  {:vein-kdt (kdtree/build-tree roots)
   :source-kdt (kdtree/build-tree sources)})

(defn step [{:as params :keys [ds di dk]} {:as state :keys [vein-kdt source-kdt]}]
  "Advances the state of the space colonization algorithm by one step."
  (let [im (influence-mapping di vein-kdt (iterate-kdtree source-kdt))
        bs (branchlets ds im)
        new-nodes (map second bs)
        vs (->> new-nodes
                (map (partial victims dk source-kdt))
                (apply set/union))]
    {:vein-kdt (reduce (partial kdtree/insert) vein-kdt new-nodes)
     :source-kdt (reduce (partial kdtree/delete) source-kdt vs)
     :branchlets bs
     :victims vs}))

(defn stopped? [{:keys [branchlets victims]}]
  "Returns true if the space colonization algorithm has stopped, otherwise false."
  (and (some? branchlets) (some? victims) (empty? branchlets) (empty? victims)))

(defn steps
  "Returns a lazy sequence of all intermediate states from the space colonization algorithm until
  completion."
  [params state]
  (take-while (complement stopped?) (iterate (partial step params) state)))
