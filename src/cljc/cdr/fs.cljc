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

  (cond
    (and (vector? n1)
         (vector? n2)) (let []
                         )
    (and (map? n1)
         (map? n2)) (merge-with into n1 n2)
    
    :else n1))

(defn merge-nodes [n1 n2]
  (merge-with merge-node-helper n1 n2))

(defn attach [node paths]
  (let [p (first paths)
        remaining-paths (-> paths rest vec)]
    (prn "node=" node)
    (prn "paths=" paths)
    (prn "p=" p)
    (prn "remaining=" remaining-paths)

    (cond
      (empty? node) (mk-node paths)
      (map? node) (if (contains? node p)
                    (let [sub-node (node p)
                          sub-node1 (attach sub-node remaining-paths)]
                      (prn "sub-node=" sub-node)
                      (prn "sub-node1=" sub-node1)
                      (if (vector? sub-node1)
                        (assoc node p sub-node1)
                        (assoc node p [sub-node1])))
                    (assoc node p [(mk-node remaining-paths)]))
      (vector? node) (if (empty? remaining-paths)
                       (let [r  (conj node p)]
                         (prn "empty node=" node " p=" p " r=" r)
                         r)
                       (let [vector-of-nodes node
                             found-node (->> vector-of-nodes
                                             (filter #(contains? % p))
                                             first)
                             without-found-node (->> vector-of-nodes
                                                     (remove #(contains? % p)))]
                         (prn "vector-of-nodes=" vector-of-nodes)
                         (prn "found-node=" found-node)

                         (if found-node
                           (let [sub-node (found-node p)
                                 new-sub-node (attach sub-node remaining-paths)]
                             (prn "sub-node=" sub-node)
                             (prn "new-sub-node=" new-sub-node)
                             (if (vector? new-sub-node)
                               (assoc found-node p
                                      new-sub-node)
                               (assoc found-node p
                                      [new-sub-node])))
                           (let [new-node (mk-node paths)]
                             (prn "new-node=" new-node)
                             (conj vector-of-nodes new-node)))))
      :else node)))



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

  {"cdr"
   [{"src"
     [{"clj" [{"cdr" ["server.clj"]}]}
      {"cljs" [{"cdr" ["core.cljs" "mdc.cljs"]}]}]}
    "project.clj"]}
  
  (def files ["/cdr/src/cljc/cdr/fs.cljc"
              ;; "/cdr/src/cljc/cdr/util.cljc"
              ;; "/cdr/src/cljc/cdr/foobar.cljc"
              "/cdr/resources/public"
              ;; "/cdr/resources/public/js/clojure.js"
              ;; "/cdr/resources/public/js/parinfer.js"
              ;; "/cdr/resources/public/css/clojure.css"
              ;; "/cdr/resources/public/css/dark.css"
              ])

  (let [paths (mapv (fn [f]
                      (-> f (str/split #"/") rest vec))
                    files)]
    (prn paths)
    (reduce attach
            {}
            paths)
    )

  {"cdr"
   [{"src" [{"cljc" [{"cdr" ["fs.cljc"]}]}]}
    {"resources" ["public"]}]}
  
  (node->path {"cdr"
               [{"src" [{"cljc" [{"cdr" ["fs.cljc" "util.cljc" "foobar.cljc"]}]}]}]})
  
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
