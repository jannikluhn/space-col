(ns space-col.core
  (:require [kdtree]
            [clojure.math.numeric-tower :as math]
            [clojure.set :as set]))

;
; some vector arithmetic
;
(defn vec+ [& vs]
  "Compute the sum of one or more vectors."
  (if (seq vs)
    (vec (apply map + vs))))

(defn vec- [& vs]
  "Compute the difference between a vector and one or more other ones.
  
  If only a single vector is given, it is inverted."
  (if (seq vs)
    (vec (apply map - vs))))

(defn vec* [v l]
  "Multiply the length of a vector by l."
  (if-not (nil? v)
    (vec (map (partial * l) v))))

(defn length [v]
  "Returns the length of vector v."
  (->> v
       (map #(* % %))
       (reduce +)
       (math/sqrt)))

(defn norm [v]
  "Normalizes vector v."
  (let [l (length v)]
    (if (> l 0) (vec* v (/ l)))))

(defn dist [p1 p2]
  "Compute the distance between two points."
  (length (vec- p1 p2)))

;
; kdtree extensions
;
(defn sphere-search [tree center radius]
  "Finds all points whose distance to `center` is less or equal than `radius`."
  (let [box (map #(vector (- % radius) (+ % radius)) center)
        in-box (kdtree/interval-search tree box)
        in-sphere (filter #(<= (dist % center) radius) in-box)]
    in-sphere))

;
; space colonization algorithm
;
; (defn init-state [attractors roots]
;   {:pool (kdtree/build-tree attractors)
;    :attractors (set attractors)
;    :new-victims #{}
;    :victims #{}
;    :new-nodes #{}
;    :nodes (set roots)
;    :new-segments #{}
;    :segments #{}})

(defn influenced-node [di vein-kdt source]
  "Returns the vein node that is influenced by the given source node if any."
  (let [nn (:point (kdtree/nearest-neighbor vein-kdt source))]
    (if (<= (dist nn source) di)
      nn)))

(defn invert-map-aggregating [m]
  "Inverts a map such that values become keys and keys are merged in sets."
  (reduce (fn [m [k v]]
            (update m v #(conj (set %) k))) {} m))

(defn influence-sets [di vein-kdt sources]
  "Returns a mapping from vein nodes to the set of source nodes they are influenced by."
  (->> sources
      (map (partial influenced-node di vein-kdt))
      (map vector sources)
      (remove (comp nil? second))
      (reduce #(assoc %1 (first %2) (second %2)) {})
      (invert-map-aggregating)))

(defn influence-dir [influence-set node]
  "Return the direction to which a node is pulled towards.
  
  The result is a normalized vector."
  (let [influence-sum (->> influence-set
                           (map #(vec- % node))
                           (map norm)
                           (remove nil?)
                           (apply vec+))]
    (norm influence-sum)))

(defn offspring [ds dir node]
  "Returns the offspring a node will have in the next step, if any."
  (if-not (nil? dir)
    (vec+ node (vec* dir ds))))

(defn offspring-map [ds is]
  "Returns a mapping between nodes and their offspring."
  (->> is
       (map (fn [[n s]] [n (offspring ds (influence-dir s n) n)]))
       (into {})))

(defn victims [dk source-kdt vein-node]
  "Returns the set of source nodes in the kdtree that are too close to a given vein node.
  
  dk denotes the kill distance."
  (if-not (nil? vein-node)
    (set (sphere-search source-kdt vein-node dk))
    #{}))

(defn col-step [{:as params :keys [ds di dk]} {:as state :keys [vein-kdt source-kdt sources]}]
  (let [is (influence-sets di vein-kdt sources)
        segs (set (offspring-map ds is))
        os (map second segs)
        vs (apply set/union (map (partial victims dk source-kdt) os))]
    {:vein-kdt (reduce (partial kdtree/insert) vein-kdt os)
     :source-kdt (reduce (partial kdtree/delete) source-kdt vs)
     :sources (set/difference sources vs)
     :new-nodes os
     :nodes (set/union os (:nodes state))
     :new-victims vs
     :victims (set/union vs (:victims state))
     :new-segments segs
     :segments (set/union segs (:segments state))
     :stopped (and (empty? os) (empty? vs))}))

(defn col-steps [params state]
  (take-while (complement :stopped) (iterate (partial col-step params) state)))

(defn init-state [sources roots]
  {:vein-kdt (kdtree/build-tree roots)
   :source-kdt (kdtree/build-tree sources)
   :sources (set sources)
   :segments #{}})
