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
    (let [next-path (first paths)
          remaining-paths (rest paths)]
      (prn " node=" node "paths=" paths " result=" result " next-path=" next-path )
      (cond
        (empty? result) (let [next-node (node next-path)]
                          (gh next-node remaining-paths [next-path]))
        (vector? node) (let [index (index-of node next-path)
                             next-node (node index)]
                         (gh next-node remaining-paths (into result [index next-path])))
        (map? node) (let [last-path (last result)
                          children (node last-path)
                          index (index-of children next-path)
                          next-node (children index)]
                      (gh next-node remaining-paths (into result [index next-path])))
        :else (prn "error")))))

(defn get-path [node path-str]
  (let [paths (clojure.string/split path-str #"/")
        paths (rest paths)]
    (gh node paths [])))

(comment
  (def root {"cdr" [{"resources" [{"public" [{"css" ["codemirror.css" "clojure.css"]}
                                             {"js" ["clojure.js" "codemirror.js"]}
                                             "index.html"]}

                                  ]}
                    {"src" [{"clj" [{"cdr" ["server.clj"]}]}
                            {"cljs" [{"cdr" ["core.cljs" "mdc.cljs"]}]}]}
                    "project.clj"
                    ]})
  
  (get-in root (get-path root "/cdr/src"))
  (get-in root (get-path root "/cdr/resources/public/js"))
  (get-in root (get-path root "/cdr/resources/public/css/"))
  (get-in root (get-path root "/cdr/resources/public/index.html"))
  (get-in root (get-path root "/cdr/src/clj/cdr"))
  
  (get-path root "/cdr/resources/public/js")
  "/cdr/resources/public/js/clojure.js"
  (get-in root ["cdr" 0 "resources" 0 "public" 1 "js" 0]) 
  
  (->> (clojure.string/split "/cdr/resources/public" #"/")
       (drop 2)

       )

  )
