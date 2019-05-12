(ns cdr.fs
  (:require [clojure.string :as str]
            [clojure.set :as s]
            [clojure.data :as d]
            [clojure.core.async :as a :include-macros true]
            [cljs-await.core :refer [await]]
            [cdr.util :as util]
            ))

(def walk-dir (fn [{:keys [dir on-file] :as params}]
                (a/go
                  (let [[err files] (a/<! (await (js/window.pfs.readdir dir)))]
                    (doseq [f files]
                      (let [f-full-path (str dir "/" f)
                            [err stat] (a/<! (await (js/window.pfs.stat f-full-path)))
                            stat (util/obj->clj stat)]
                        (if (= (stat "type") "dir")
                          (a/<! (walk-dir (merge params
                                                 {:dir f-full-path})))
                          (when on-file
                            (on-file f-full-path)))))))))

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

(defn attach [node paths]
  (let [p (first paths)
        remaining-paths (-> paths rest vec)]
    (cond
      (empty? node) (mk-node paths)
      (map? node) (if (contains? node p)
                    (let [sub-node (node p)
                          sub-node (attach sub-node remaining-paths)]
                      (if (vector? sub-node)
                        (assoc node p sub-node)
                        (assoc node p [sub-node])))
                    (assoc node p [(mk-node remaining-paths)]))
      (vector? node) (if (empty? remaining-paths)
                       (conj node p)
                       (let [vector-of-nodes node
                             found-node (->> vector-of-nodes
                                             (filter #(contains? % p))
                                             first)
                             without-found-node (->> vector-of-nodes
                                                     (remove #(contains? % p))
                                                     vec)]
                         (if found-node
                           (let [sub-node (found-node p)
                                 new-sub-node (attach sub-node remaining-paths)]
                             (if (vector? new-sub-node)
                               (conj without-found-node
                                     (assoc found-node p
                                            new-sub-node))
                               (assoc found-node p
                                      [new-sub-node])))
                           (conj node (mk-node paths)))))
      :else node)))

(defn mk-project-tree [files]
  (let [paths (mapv (fn [f]
                      (-> f (str/split #"/") rest vec))
                    files)]
    (reduce attach
            {}
            paths)))

(comment
  (node->path {"cdr" [{"src" ["clojure.clj"]}]})
  (node->path {"cdr"
               [{"src" [{"cljc" [{"cdr" ["fs.cljc" "util.cljc" "foobar.cljc"]}]}]}]})
  
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
              "/cdr/src/cljc/cdr/foobar.cljc"
              "/cdr/resources/public/js/clojure.js"
              "/cdr/resources/public/js/parinfer.js"
              "/cdr/resources/public/css/clojure.css"
              "/cdr/resources/public/css/dark.css"
              ])

  (mk-project-tree files)
  )