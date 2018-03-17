(defproject com.ben-allred/collaj "0.8.0"
    :description "A state management system for Clojure/ClojureScript based on Redux"
    :url "https://www.github.com/skuttleman/collaj"
    :license {:name "Eclipse Public License"
              :url  "http://www.eclipse.org/legal/epl-v10.html"}
    :min-lein-version "2.6.1"
    :source-paths ["src/cljc"]
    :test-paths ["test/cljc"]
    :dependencies [[org.clojure/clojure "1.9.0"]
                   [org.clojure/clojurescript "1.9.946"]
                   [org.clojure/core.async "0.3.465"]]
    :plugins [[lein-cljsbuild "1.1.6"]
              [com.jakemccrary/lein-test-refresh "0.22.0"]]
    :hooks [leiningen.cljsbuild]
    :cljsbuild {:builds {:collaj {:source-paths ["src/cljc"]
                                  :compiler     {:output-to     "resources/public/js/collaj.js"
                                                 :optimizations :advanced
                                                 :pretty-print  false}}}})
