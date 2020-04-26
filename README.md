# space-col

This Clojure library implements the so called space colonization algorithm which can be used to
generate naturally looking structures like tree crowns, leaves, or vein systems.

## The Algorithm

The general idea of the space colonization algorithm is as follows: In a first step, many source
nodes are randomly distributed in a region of space around a few root nodes. Sources exert
attractive forces, leading to veins growing from the roots towards nearby sources. Whenever a vein
comes too close to a source, the source is removed, allowing the vein to continue to grow towards
other sources. Eventually, the whole region previously covered in source nodes will be pervaded by
veins.

For a more detailed description, see the following papers:

- Runions, Adam, et al. "Modeling and visualization of leaf venation patterns." ACM SIGGRAPH 2005
  Papers. 2005. 702-711.
- Runions, Adam, Brendan Lane, and Przemyslaw Prusinkiewicz. "Modeling Trees with a Space
  Colonization Algorithm." NPH 7 (2007): 63-70.


## Installation

This library is released on [https://clojars.ort], latest stable release is `0.1.0`.

Leiningen dependency information:

```
[jannikluhn/space-col "0.1.0"]
```

The following description assumes that the main namespace is required like this:

```Clojure
(ns your.namespace
  (:require [jannikluhn.space-col.core :as sc]))
```

## Usage

### Overview

The function interfaces of this library are based around maps containing the state and its
incremental changes. They are used both as parameters and return values. The initial state map is
created using `(sc/init roots sources)` taking the initial root and source nodes as parameters.

`(sc/step params state)` computes the next iteration of the algorithm. `params` is a map containing
various parameters of the algorithm described in detail below.

The second argument is the current state to evolve, either created by `sc/init` or by an earlier
call of `sc/step`. The return value is in turn a state map.

The convenience function `(sc/steps params state)` creates a lazy sequence of state maps created
by repeatedly applying `sc/step` until the algorithm is completed.

`(sc/inject-roots roots state)` and `(sc/inject-sources sources state)` provide the ability to add
new roots and sources on the fly between two steps.

### State Maps

State maps have the following entries:

- `::sc/vein-kdt`: A [k-d tree](https://github.com/abscondment/clj-kdtree) containing all vein nodes.
- `::sc/source-kdt`: A [k-d tree](https://github.com/abscondment/clj-kdtree) containing all source
  nodes.
- `::sc/branchlets`: The set of branchlets that have been created in the last iteration step, each
  of the form `[[start-x start-y] [end-x end-y]]`.
- `::sc/victims`: The set of source nodes which have been removed in the last iteration step.
- `::sc/stopped`: `true` if the algorithm is making progress, `false` if it has converged.

Note that there is no entry for, e.g., the whole vein network, the number of steps, or the length
of a branch. The caller is expected to aggregate the information they are interested in themselves
by use of `map`, `reduce`, etc.

### Parameters

There are three parameters available to tweak the behavior of the algorithm:

- The step distance `::sc/ds`: The length by which a vein grows per iteration step.
- The influence distance `::sc/di`: The maximum distance at which a source node attracts a vein
  node.
- The kill distance `::sc/dk`: If the distance between a vein and a source node falls below this
  value, the source node will be removed.

Both `sc/step` and `sc/steps` expect a map containing these as their first argument.

### Examples

The examples are split into two parts:

- Examples demonstrating how to create sequences of state maps
- Examples making suggestions how to extract interesting information from sequences of state maps

Of course, in order to get a result, the two steps have to be combined.

#### State Sequences

```Clojure
(->> (sc/init [[250 400]] [[140 130] [360 200] [200 100]])
     (sc/steps {::sc/ds 10 ::sc/di 300 ::sc/dk 10})
```

```Clojure
(let [sources (repeatedly 100 #(vector (rand 500) (rand 500)))]
  (->> (sc/init [[250 250]] sources)
       (sc/steps {::sc/ds 2 ::sc/di 30 ::sc/dk 2})))
```

```Clojure
(let [s0 (sc/init [[250 250]])
      sources (repeatedly #(vector (rand 500) (rand 500)))
      injectors (map #(partial sc/inject-sources [%]) roots)
      steppers (repeat (partial sc/step {::sc/ds 10 ::sc/di 300 ::sc/dk 10}))]
  (->> s0
       (apply comp (interleave injectors steppers))
       (take 100)))
```

#### Aggregation

```Clojure
(->> steps
     (map ::sc/branchlets)
     (apply set/union))
```

```Clojure
(->> steps
     (map ::sc/branchlets)
     (map #(map second %)))
```

```Clojure
(->> steps
     (map ::sc/victims)
     (map count)
     (reductions +))
```

```Clojure

```

## License

Copyright © 2020 Jannik Luhn

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
