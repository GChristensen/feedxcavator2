(defproject feedxcavator "2.0.1-SNAPSHOT"
  :description "A HTML to RSS Converter"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.macro "0.1.2"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/clojurescript "1.9.521"]
                 [compojure "1.6.0"]
                 [enlive "1.1.6"]
                 [clj-time "0.13.0"]
                 [xerces/xercesImpl "2.11.0"]
                 [appengine-magic "0.5.1-SNAPSHOT"]
                 ]
  :plugins [[appengine-magic "0.5.1-SNAPSHOT"]
            [lein-cljsbuild "1.1.6"]
            ]
  :aot [feedxcavator.app_servlet
        feedxcavator.app_context]
  :javac-options ["-target" "1.6" "-source" "1.6"]
  :source-paths ["src/main/clj"]
  :java-source-paths ["src/main/java"] ; Java source is stored separately.
  :test-paths ["test" "src/test/clj"]
  :resource-paths ["src/main/resources"]
  :appengine-app-versions {:myxcavator "private"}
  :cljsbuild {
              :builds [{
                        ; The path to the top-level ClojureScript source directory:
                        :source-paths ["src/main/cljs"]
                        ; The standard ClojureScript compiler options:
                        ; (See the ClojureScript compiler documentation for details.)
                        :compiler     {
                                       :output-to   "war/js/main.js"
                                       :optimizations :advanced ;:whitespace
                                       :infer-externs true
                                       :pretty-print  false; true
                                       }}]})