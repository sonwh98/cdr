(defproject  cdr "0.0.1"
  :min-lein-version "2.8.3" 
  :dependencies [[org.clojure/clojure "1.10.0"]  
                 [com.kaicode/wocket "0.1.5-SNAPSHOT"]
                 [org.clojure/clojurescript "1.10.520"]
                 [compojure "1.6.1"]
                 [reagent "0.8.1"]
                 [stigmergy/mr-clean "0.1.0-SNAPSHOT"]
                 [cljs-await "1.0.2"]
                 [org.clojure/core.async "0.4.490"]
                 ]

  :plugins [[lein-figwheel "0.5.18"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                    :target-path]

  :figwheel {:server-port 3450}
  
  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/cljs"]
                :figwheel {:on-jsload "cdr.core/jsload"
                           :websocket-host :js-client-host}
                :compiler {:main cdr.core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/cdr.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true
                           :preloads [devtools.preload]}}
               {:id "min"
                :source-paths ["src/cljs"]
                :compiler {:output-to "resources/public/js/compiled/cdr.min.js"
                           :optimizations :advanced}}]}
  
  :profiles {:project/dev {:dependencies [[binaryage/devtools "0.9.10"]
                                          [figwheel-sidecar "0.5.18"]
                                          [cider/piggieback "0.4.0"]]
                           :source-paths ["src/cljs" "src/clj" "env/dev/clj"] 
                           }
             :project/prod {:prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                            :source-paths ["src/cljs" "src/clj"]
                            :main cdr.server
                            :aot :all}

             :dev [:project/dev]
             :uberjar [:project/prod]
             }

  )
