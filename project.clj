(defproject space-col "0.1.0-SNAPSHOT"
  :description "An implementation of the space colonization algorithm."
  :url "http://github.com/jannikluhn/space-col"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [clj-kdtree "1.2.0"]]
  :repl-options {:init-ns space-col.core})
