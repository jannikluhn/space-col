(ns space-col.euclid
  (:require [clojure.math.numeric-tower :as math]))

(defn vec+
  "Compute the sum of one or more vectors."
  [& vs]
  (if (seq vs)
    (vec (apply map + vs))))

(defn vec-
  "Compute the difference between a vector and one or more other ones.
  
  If only a single vector is given, it is inverted."
  [& vs]
  (if (seq vs)
    (vec (apply map - vs))))

(defn vec*
  "Multiply the length of a vector by l."
  [v l]
  (if-not (nil? v)
    (vec (map (partial * l) v))))

(defn length
  "Returns the length of vector v."
  [v]
  (->> v
       (map #(* % %))
       (reduce +)
       (math/sqrt)))

(defn norm
  "Normalizes vector v."
  [v]
  (let [l (length v)]
    (if (> l 0) (vec* v (/ l)))))

(defn dist
  "Compute the distance between two points."
  [p1 p2]
  (length (vec- p1 p2)))
