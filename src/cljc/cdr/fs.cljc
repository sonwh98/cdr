(ns cdr.fs
  (:require [clojure.string :as str]))

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

(defn- get-path-helper [node paths result]
  (if (empty? paths)
    result
    (let [next-path (first paths)
          remaining-paths (rest paths)]
      (cond
        (empty? result) (let [next-node (node next-path)]
                          (get-path-helper next-node remaining-paths [next-path]))
        (vector? node) (let [index (index-of node next-path)
                             next-node (node index)]
                         (get-path-helper next-node remaining-paths (into result [index next-path])))
        (map? node) (let [last-path (last result)
                          children (node last-path)
                          index (index-of children next-path)
                          next-node (children index)]
                      (get-path-helper next-node remaining-paths (into result [index next-path])))
        :else (prn "error")))))

(defn get-path [node path-str]
  (let [paths (str/split path-str #"/")
        paths (rest paths)]
    (get-path-helper node paths [])))

(defn- map-value->vector [m]
  (let [k (-> m keys first)
        v (m k)]
    (if (map? v)
      {k [(map-value->vector v)]}
      {k v})))

(defn mk-node [file-path-str]
  (let [paths (str/split file-path-str #"/")
        paths (-> paths rest)
        tree (if (> (count paths) 2)
               (let [leaf (last paths)
                     paths (drop-last paths)]
                 (assoc-in {} paths [leaf]))
               (assoc-in {} paths []))]
    (map-value->vector tree)))

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
  (get-in root (get-path root "/cdr/src/clj/cdr"))

  (def files ["/cdr/src/cljc/cdr/fs.cljc"
              "/cdr/src/cljc/cdr/util.cljc"
              "/cdr/resources/public/js/codemirror.js"
              "/cdr/resources/public/js/clojure.js"
              "/cdr/resources/public/js/parinfer.js"
              "/cdr/resources/public/css/clojure.css"
              "/cdr/resources/public/css/dark.css"])

  (def n1 (mk-node "/cdr/src/cljc/cdr/fs.cljc"))
  (def n2 (mk-node "/cdr/src/cljc/cdr/util.cljc"))

  (defn merge-nodes [n1 n2]
    (prn "n1=" n1)
    (prn "n2="  n2)
    (if (and (vector? n1)
             (vector? n2))
      
      )
    n1)
  
  (merge-with merge-nodes n1 n2)
  
  (mk-node "/cdr/resources")
  )
