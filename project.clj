(defproject feedxcavator "2.1.0-SNAPSHOT"
  :description "A programmable RSS server"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.macro "0.1.5"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/clojurescript "1.10.520"]
                 [ring/ring-json "0.5.0"]
                 [compojure "1.6.1"]
                 [enlive "1.1.6"]
                 [hiccup "1.0.5"]
                 [crate "0.2.4"]
                 [org.flatland/ordered "1.5.7"]
                 [prismatic/dommy "1.1.0"]
                 [cljs-ajax "0.8.0"]
                 [io.forward/yaml "1.0.9"]
                 [appengine-magic "0.5.1-SNAPSHOT"]
                 [com.oscaro/clj-gcloud-storage "0.71-1.2"]
                 [net.sourceforge.htmlunit/htmlunit "2.36.0"]
                 ;[clj-http "3.10.0"]
                 ]
  :plugins [[appengine-magic "0.5.1-SNAPSHOT"]
            [lein-cljsbuild "1.1.7"]
            ]
  :aot [feedxcavator.app_servlet
        feedxcavator.app_context]
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :source-paths ["src/main/clj"]
  :java-source-paths ["src/main/java"] ; Java source is stored separately.
  :test-paths ["test" "src/test/clj"]
  :resource-paths ["src/main/resources"]
  :appengine-app-versions {:myxcavator "private"}
  :repl-options {:init (load-file ".local/start-local-server.clj")}
  :cljsbuild {
              :builds [{
                        ; The path to the top-level ClojureScript source directory:
                        :source-paths ["src/main/cljs"]
                        ; The standard ClojureScript compiler options:
                        ; (See the ClojureScript compiler documentation for details.)
                        :compiler     {
                                       :output-to   "war/js/main.js"
                                       :optimizations :advanced ;:whitespace ;:advanced
                                       :pretty-print  false ;true ;false
                                       :infer-externs true
                                       :externs ["src/main/js/externs.js"
                                                 "war/js/jstree/jstree.js"]
                                       }}]})