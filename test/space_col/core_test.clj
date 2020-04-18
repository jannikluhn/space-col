(ns space-col.core-test
  (:require [clojure.test :refer :all]
            [space-col.core :refer :all]))

(deftest vec+-test
  (is (nil? (vec+)))
  (is (= (vec+ [1 2] [3 5]) [4 7]))
  (is (= (vec+ [5 10] (vec- [5 10])) [0 0])))

(deftest vec-test
  (is (= (vec- [1 2]) [-1 -2]))
  (is (= (vec- [1 2] [3 1]) [-2 1]))
  (is (= (vec- [5 3] [2 1] [3 2]) [0 0])))

(deftest scale-test
  (is (= (vec* [3 4] 5) [15 20]))
  (is (= (vec* [] 4) []))
  (is (= (vec* [0 0] 2) [0 0])))

(deftest length-test
  (is (= (length [2]) 2))
  (is (= (length [3 4]) 5)))

(deftest norm-test
  (is (= (norm [2]) [1]))
  (is (= (norm [0 0 4 0]) [0 0 1 0]))
  (is (< 0.99 (length (norm [1 2 3])) 1.01)))

(deftest dist-test
  (is (= (dist [0] [5]) 5))
  (is (= (dist [0 0] [3 4]) 5))
  (is (= (dist [-4 8] [-1 4]) 5)))

(deftest sphere-search-test
  (let [points [[0 0] [0 1] [1 0] [1 1]]
        pool (kdtree/build-tree points)]
    (is (empty? (sphere-search pool [0.5 0.5] 0.7)))
    (is (count (sphere-search pool [0.5 0.5] 0.8)) 4)))
