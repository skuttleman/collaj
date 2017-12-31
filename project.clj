(defproject com.ben-allred/collaj "0.2.0"
    :description "A state management system for Clojure/ClojureScript based on Redux"
    :url "https://www.github.com/skuttleman/collaj"
    :license {:name "Eclipse Public License"
              :url  "http://www.eclipse.org/legal/epl-v10.html"}
    :min-lein-version "2.6.1"
    :source-paths ["src/cljc"]
    :test-paths ["test/cljs"]
    :dependencies [[org.clojure/clojure "1.8.0"]
                   [org.clojure/clojurescript "1.9.542"]]
    :plugins [[lein-cljsbuild "1.1.6"]]
    :hooks [leiningen.cljsbuild]
    :cljsbuild {:builds        {:collaj {:source-paths ["src/cljc"]
                                         :compiler     {:output-to     "resources/public/js/collaj.js"
                                                        :optimizations :advanced
                                                        :pretty-print  false}}
                                :test   {:source-paths ["src/cljc" "test/cljs"]
                                         :test-paths   ["test/cljs"]
                                         :compiler     {:output-to     "resources/main-test.js"
                                                        :optimizations :whitespace
                                                        :pretty-print  true}}}
                :test-commands {"unit" ["phantomjs"
                                        "resources/phantom-runner.js"
                                        "resources/phantom.html"]}})
