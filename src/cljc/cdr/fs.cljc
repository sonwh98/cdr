(ns cdr.fs
  (:require [clojure.string :as str]
            [clojure.set :as s]))

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

(defn mk-node
  ([dir-path files]
   (let [dir-path (if (string? dir-path)
                    (-> dir-path (str/split #"/") rest)
                    dir-path)
         tree (assoc-in {} dir-path files)]
     (map-value->vector tree)))
  ([file-path]
   (let [file-path (if (string? file-path)
                     (-> file-path (str/split #"/") rest)
                     file-path)
         file (last file-path)
         dir-path (drop-last file-path)]
     (mk-node dir-path [file]))))

(defn- node->path-helper [node path]
  (cond
    (map? node) (let [k (-> node keys first)
                      v (node k)
                      new-path (conj path k)]
                  (node->path-helper v new-path))
    (and (vector? node)
         (-> node first map?)) (let [a-map (first node)]
                                 (node->path-helper a-map path))
    (vector? node) (let [p (first node)]
                     (conj path p))
    :else path))

(defn node->path [node]
  (node->path-helper node []))

(defn node->path-str [node]
  (let [path (node->path node)]
    (str "/" (str/join "/" path))))

(defn- merge-node-helper [n1 n2]
  (if (and (vector? n1)
           (vector? n2))
    (let [p1 (node->path n1)
          p2 (node->path n2)
          s1 (set p1)
          s2 (set p2)
          common (vec (s/intersection s1 s2))
          different (vec (into (s/difference s1 s2)
                               (s/difference s2 s1)))]
      (mk-node common different))
    n1))

(defn merge-nodes [n1 n2]
  (merge-with merge-node-helper n1 n2))

(comment
  (def n {"cdr" [{"src" ["clojure.clj"]}]})
  (node->path n)
  
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
  (def n2a (mk-node "/cdr/src/cljc/cdr" ["foo.cljc" "bar.cljc"]))

  (mk-node ["cdr" "src" "cljc" "cdr" "fs.cljc"])
  (mk-node ["cdr" "src" "cljc" "cdr"] ["fs.cljc" "bar.cljc"])
  (mk-node "/cdr/src/cljc/cdr" ["fs.cljc" "bar.cljc"])
  
  (def n3 (merge-nodes n1 n2))
  (node->path n2)
  (node->path-str n2)
  
  (mk-node "/cdr/resources")
  (s/intersection #{1 3} #{1 2})
  )
