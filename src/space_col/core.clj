(ns space-col.core
  (:require [clojure.set :as set]
            [kdtree]
            [space-col.euclid :as eu]
            [space-col.utils :as utils]
            [same :refer [ish?]]
            [clojure.spec.alpha :as s]))

;
; Spec
;
(s/def ::di number?)
(s/def ::ds number?)
(s/def ::dk number?)
(s/def ::params (s/keys :req [::di ::ds ::dk]))

(s/def ::kdt (s/or :empty nil? :non-empty record?))
(s/def ::vein-kdt ::kdt)
(s/def ::source-kdt ::kdt)
(s/def ::point (s/coll-of number? :kind vector?))
(s/def ::branchlet (s/coll-of ::point :kind vector? :count 2))
(s/def ::branchlets (s/coll-of ::branchlet :kind set?))
(s/def ::victims (s/coll-of ::point :kind set?))
(s/def ::stopped boolean?)
(s/def ::state (s/keys :req [::vein-kdt
                             ::source-kdt
                             ::branchlets
                             ::victims
                             ::stopped]))

;
; Space colonization algorithm
;
(defn influenced-node
  "Returns the vein node that is influenced by the given source node, if any.

  `di` denotes the influence distance."
  [di vein-kdt source]
  (let [nn (:point (kdtree/nearest-neighbor vein-kdt source))]
    (if (<= (eu/dist nn source) di)
      nn)))

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
       (utils/invert-map-aggregating)))

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
    (set (utils/sphere-search source-kdt vein-node dk))
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
  {::vein-kdt (kdtree/build-tree roots)
   ::source-kdt (kdtree/build-tree sources)
   ::branchlets #{}
   ::victims #{}
   ::stopped false})

(defn step
  "Advances the state by one step."
  [{:as params :keys [::ds ::di ::dk]} {:as state :keys [::vein-kdt ::source-kdt]}]
  (let [im (influence-mapping di vein-kdt (utils/iterate-kdtree source-kdt))
        bs-with-overlap (branchlets ds im)
        bs (filter (complement (partial overlapping-branchlet? vein-kdt)) bs-with-overlap)
        new-nodes (map second bs)
        vs (->> new-nodes
                (map (partial victims dk source-kdt))
                (apply set/union))]
    {::vein-kdt (reduce kdtree/insert vein-kdt new-nodes)
     ::source-kdt (reduce kdtree/delete source-kdt vs)
     ::branchlets (set bs)
     ::victims vs
     ::stopped (and (empty? bs) (empty? vs))}))

(defn steps
  "Returns a lazy sequence of all intermediate states until completion."
  [params state]
  (take-while (complement ::stopped) (iterate (partial step params) state)))
