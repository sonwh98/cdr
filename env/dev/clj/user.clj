(ns user
  (:require [figwheel-sidecar.repl-api :as f]
            [cdr.server :as s]
            [taoensso.timbre :as log :include-macros true]))

(defn start []
  (s/start)
  (f/start-figwheel!))

(defn stop []
  (f/stop-figwheel!)
  (s/stop))

(defn cljs []
  (f/cljs-repl))

(log/set-level! :info)

(comment

  (def root {"cdr" [{"resources" [{"public" [{"css" ["codemirror.css" "clojure.css"]}
                                             {"js" ["clojure.js" "codemirror.js"]}
                                             "index.html"]}

                                  ]}
                    {"src" [{"clj" [{"cdr" ["server.clj"]}]}
                            {"cljs" [{"cdr" ["core.cljs" "mdc.cljs"]}]}]}
                    "project.clj"
                    ]})
  
  (defn index-of [v-of-m k]
    (let [index-key (map-indexed (fn [i m]
                                   (if (map? m)
                                     [i (-> m keys first)]
                                     [i nil]))
                                 v-of-m)
          found-index-key (filter (fn [[index a-key]]
                                    (when (= a-key k)
                                      true))
                                  index-key)]
      (ffirst found-index-key)))

  (defn gh [node paths result]
    (if (empty? paths)
      result
      (let [_ (prn "paths=" paths " result=" result " node=" node)
            path-0 (first paths)
            _ (prn "path-0=" path-0)
            path-1 (second paths)
            _ (prn "path-1=" path-1)
            remaining-paths (drop 2 paths)
            _ (prn "remaining-paths=" remaining-paths)
            sub-dirs (if (vector? node)
                       (let [i (index-of node path-0)]
                         (node i))
                       (node path-0))
            _ (prn "sub-dirs=" sub-dirs)
            index-1 (index-of sub-dirs path-1)
            _ (prn "index-1=" index-1)
            path [path-0 index-1 path-1]
            _ (prn "path=" path)
            node (get-in node path)
            _ (prn "node=" node)]

        (gh node remaining-paths (concat result
                                         path))
        )))
  
  (defn get-path [node path-str]
    (let [paths (rest (clojure.string/split path-str #"/"))]
      (gh node paths [])))

  (get-path root "/cdr/src") ["cdr" 1]
  (get-path root "/cdr/resources/public") 
  )
