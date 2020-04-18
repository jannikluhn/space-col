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
; space colonization helpers
;
(defn attractors [di pool node]
  "Returns the set of points from the pool which have an influence on a given node.
  
  di denotes the influence distance."
  (sphere-search pool node di))

(defn attraction-dir [attractors node]
  "Return the direction to which a node is pulled towards by a set of attractors.
  
  The result is a normalized vector."
  (let [attr-sum (->> attractors
                      (map #(vec- % node))
                      (map norm)
                      (remove nil?)
                      (apply vec+))]
    (norm attr-sum)))

(defn victims [dk pool node]
  "Returns the set of attractors in the pool that are too close to a given node.
  
  dk denotes the kill distance."
  (if-not (nil? node)
    (set (sphere-search pool node dk))
    #{}))

(defn offspring [ds node attraction-dir]
  "Returns the offspring a node will have in the next step, if any."
  (if-not (nil? attraction-dir)
    (vec+ node (vec* attraction-dir ds))))

;
; space colonization algorithm
;
(defn init-state [attractors roots]
  {:pool (kdtree/build-tree attractors)
   :attractors (set attractors)
   :new-victims #{}
   :victims #{}
   :new-nodes #{}
   :nodes (set roots)
   :new-segments #{}
   :segments #{}})

(defn col-step-node [ds di dk pool node]
  (let [o (offspring ds node (attraction-dir (attractors di pool node) node))
        new-nodes (if o #{o} #{})]
    {:new-victims (set/union (victims dk pool o) (victims dk pool node))
     :new-nodes (if o #{o} #{})
     :new-segments (if o #{[node o]} #{})}))

(defn col-step [ds di dk {:keys [pool attractors victims nodes segments] :as state}]
  (let [node-step-results (map (partial col-step-node ds di dk pool) nodes)
        new-victims (->> node-step-results (map :new-victims) (apply set/union))
        new-nodes (->> node-step-results (map :new-nodes) (apply set/union))
        new-segments (->> node-step-results (map :new-segments) (apply set/union))
        stopped (every? :stopped node-step-results)]
    {:pool (reduce kdtree/delete pool victims)
     :attractors (set/difference attractors new-victims)
     :new-victims new-victims
     :victims (set/union victims new-victims)
     :new-nodes new-nodes
     :nodes (set/union nodes new-nodes)
     :new-segments new-segments
     :segments (set/union segments new-segments)
     :stopped (and (empty? new-nodes) (empty? new-victims))}))

(defn col-steps [ds di dk state]
  (take-while (complement :stopped) (iterate (partial col-step ds di dk) state)))
