(defproject clojure-navigation "1.0.0-SNAPSHOT"
  :description "Tools for navigating code and data structures from Clojure along with some generic helpers."
  :url "https://github.com/shopsmart/clojure-navigation"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.2.6"]]
  :jar-name "clojure-navigation.jar"
  :uberjar-name "clojure-navigation-uber.jar"
  :jvm-opts ["-Xmx10g" "-Xms512m" "-XX:+UseParallelGC"]
  :profiles {:uberjar {:aot :all}})
