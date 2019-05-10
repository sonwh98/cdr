(ns cdr.fs
  (:require [clojure.string :as str]
            [clojure.set :as s]
            [clojure.data :as d]))

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

(defn get-path [node path]
  (let [path (if (string? path)
               (-> path (str/split #"/") rest)
               path)]
    (get-path-helper node path [])))

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
  (prn "n1=" n1)
  (prn "n2=" n2)

  (if (and (vector? n1)
           (vector? n2))
    (let [a (node->path n1)
          b (node->path n2)
          [a-only b-only both-a-b] (d/diff a b)
          common both-a-b
          path (get-path (first n1) common)
          _ (prn "path=" path)
          union (->> (concat a-only b-only)
                     (remove nil?)
                     vec)
          _ (prn "union a b=" union)
          a-children (get-in (first n1)
                             path)
          difference (->> (concat a-children b-only)
                          (remove nil?)
                          vec)
          merged-node (mk-node common difference)]
      (prn "a=" a)
      (prn "b=" b)
      (prn "common=" common)
      (prn "a-children=" a-children)
      (prn "difference=" difference)
      [merged-node])
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
  (get-path {"resources" [{"public" [{"css" ["codemirror.css" "docs.css"]}]}]}
            ["resources" "public" "css"])
  
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

  (def n1 (mk-node "/cdr/resources/pubic/css/codemirror.css"))
  (def n2 (mk-node "/cdr/resources/pubic/css/doc.css"))
  (d/diff n1 n2)
  (def n3  (merge-nodes n1 n2))
  (def n2a (mk-node "/cdr/src/cljc/cdr" ["foo.cljc" "bar.cljc"]))

  (mk-node ["cdr" "src" "cljc" "cdr" "fs.cljc"])
  (mk-node ["cdr" "src" "cljc" "cdr"] ["fs.cljc" "bar.cljc"])
  (mk-node "/cdr/src/cljc/cdr" ["fs.cljc" "bar.cljc"])
  
  (def n3 (merge-nodes n1 n2))
  (node->path n2)
  (node->path-str n2)
  
  (def nodes [{"cdr" [{"resources" [{"public" [{"css" ["codemirror.css" ]}]}]}]}
              #_{"cdr" [{"resources" [{"public" [{"css" ["docs.css"]}]}]}]}
              #_{"cdr" [{"resources" [{"public" [{"css" ["dracula.css"]}]}]}]}
              #_{"cdr"
                 [{"resources"
                   [{"public" [{"css" ["material-components-web.min.css"]}]}]}]}
              {"cdr" [{"resources" [{"public" [{"js" ["active-line.js"]}]}]}]}
              ])

  (reduce (fn [root node]
            (merge-nodes root node)
            )
          nodes
          )

  (get-in (nodes 0)
          (get-path (nodes 0) "/cdr/resources/public/css/"))
  
  ({"cdr" [{"resources" [{"public" [{"css" ["codemirror.css"]}]}]}]}
   {"cdr" [{"resources" [{"public" [{"css" ["docs.css"]}]}]}]}
   {"cdr" [{"resources" [{"public" [{"css" ["dracula.css"]}]}]}]}
   {"cdr"
    [{"resources"
      [{"public" [{"css" ["material-components-web.min.css"]}]}]}]}
   {"cdr" [{"resources" [{"public" [{"js" ["active-line.js"]}]}]}]}
   {"cdr" [{"resources" [{"public" [{"js" ["clojure-parinfer.js"]}]}]}]}
   {"cdr" [{"resources" [{"public" [{"js" ["closebrackets.js"]}]}]}]}
   {"cdr" [{"resources" [{"public" [{"js" ["codemirror.js"]}]}]}]}
   {"cdr" [{"resources" [{"public" [{"js" ["lightning-fs.min.js"]}]}]}]}
   {"cdr" [{"resources" [{"public" [{"js" ["matchbrackets.js"]}]}]}]}
   {"cdr"
    [{"resources"
      [{"public" [{"js" ["material-components-web.min.js"]}]}]}]}
   {"cdr"
    [{"resources" [{"public" [{"js" ["parinfer-codemirror.js"]}]}]}]}
   {"cdr" [{"resources" [{"public" [{"js" ["parinfer.js"]}]}]}]}
   {"cdr" [{"resources" [{"public" [{"js" ["clojure.js"]}]}]}]}
   {"cdr" [{"resources" [{"public" ["index.html"]}]}]}
   {"cdr" [{"src" [{"clj" [{"cdr" ["server.clj"]}]}]}]}
   {"cdr" [{"src" [{"clj" ["user.clj"]}]}]}
   {"cdr" [{"src" [{"cljs" [{"cdr" ["mdc.cljs"]}]}]}]}
   {"cdr" [{"src" [{"cljs" [{"cdr" ["core.cljs"]}]}]}]}
   {"cdr" ["project.clj"]})
  )
