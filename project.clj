(defproject newsboard "0.1.0-SNAPSHOT"
            :description "A newsboard similar to hacker news!"
            :dependencies [[org.clojure/clojure "1.4.0"]
                           [noir "1.3.0-beta3"]
                           [com.taoensso/carmine "0.11.2"]
                           [org.clojure/math.numeric-tower "0.0.1"]
                           [clj-http "0.5.6"]
                           [org.clojure/data.json "0.2.1"]]
            :main newsboard.server)
