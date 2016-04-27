(defproject com.github.shopsmart/clojure-navigation "1.0.1"
  :description "Tools for navigating code and data structures from Clojure along with some generic helpers."
  :url "https://github.com/shopsmart/clojure-navigation"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure.gaverhae/okku "0.1.4"]]
  :jvm-opts ["-Xmx10g" "-Xms512m" "-XX:+UseParallelGC"]
  :profiles {:uberjar {:aot :all}})
