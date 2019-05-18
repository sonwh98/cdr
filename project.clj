(defproject  stigmergy/cdr "0.0.1"
  :min-lein-version "2.8.3" 
  :dependencies [[org.clojure/clojure "1.10.0"]  
                 [com.kaicode/wocket "0.1.5-SNAPSHOT"]
                 [org.clojure/clojurescript "1.10.520"]
                 [compojure "1.6.1"]
                 [reagent "0.8.1"]
                 [stigmergy/mr-clean "0.1.0-SNAPSHOT"]
                 [stigmergy/tily "0.1.8-SNAPSHOT"]
                 [com.kaicode/tweenie "0.1.1-SNAPSHOT"]
                 [cljs-await "1.0.2"]
                 [org.clojure/core.async "0.4.490"]
                 [binaryage/devtools "0.9.10"]
                 ]

  :plugins [[lein-figwheel "0.5.18"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]

  :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                    :target-path]

  :figwheel {:server-port 3449}
  
  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                :figwheel {:on-jsload "stigmergy.cdr.core/jsload"
                           :websocket-host :js-client-host}
                :compiler {:main stigmergy.cdr.init
                           :asset-path "js/compiled/dev"
                           :output-to "resources/public/js/compiled/cdr.js"
                           :output-dir "resources/public/js/compiled/dev"
                           :source-map-timestamp true
                           :preloads [devtools.preload]}}
               {:id "prod"
                :source-paths ["src/cljs" "src/cljc" "env/prod/cljs"]
                ;; :figwheel {:on-jsload "stigmergy.cdr.core/jsload"
                ;;            :websocket-host :js-client-host}
                :compiler {:main stigmergy.cdr.init
                           :asset-path "js/compiled/prod"
                           :output-to "resources/public/js/compiled/cdr.js"
                           :output-dir "resources/public/js/compiled/prod"
                           :source-map-timestamp true
                           :preloads [devtools.preload]}}
               ]}
  
  :profiles {:project/dev {:dependencies [[figwheel-sidecar "0.5.18"]
                                          [cider/piggieback "0.4.0"]]
                           :source-paths ["src/clj" "src/cljc" "env/dev/clj"]}
             :project/prod {:prep-tasks ["compile" ["cljsbuild" "once" "prod"]]
                            :source-paths ["src/clj" "src/cljc"]
                            :main stigmergy.cdr.server
                            :aot :all}

             :dev [:project/dev]
             :uberjar [:project/prod]
             }

  )
