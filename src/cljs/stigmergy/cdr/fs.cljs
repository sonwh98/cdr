(ns stigmergy.cdr.fs
  (:require [clojure.string :as str]
            [clojure.set :as s]
            [clojure.data :as d]
            [clojure.core.async :as a :include-macros true]
            [cljs-await.core :refer [await]]
            [stigmergy.tily.js :as util]
            ))

(defn walk-dir [{:keys [dir on-file] :as params}]
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
              (on-file f-full-path))))))))

(defn ls [dir]
  (a/go
    (let [[err files] (a/<! (await (js/window.pfs.readdir dir)))]
      (if err
        err
        (seq files)))))

(defn- map-value->vector [m]
  (let [k (-> m keys first)
        v (m k)]
    (if (map? v)
      {k [(map-value->vector v)]}
      {k v})))

(defn mk-node
  ([dir-path files complete-path]
   (let [files (mapv (fn [f]
                       {:name f
                        :dir-path (-> complete-path drop-last vec)})
                     files)
         tree (assoc-in {} dir-path files)]
     (map-value->vector tree)))
  ([file-path complete-path]
   (let [file (last file-path)
         dir-path (-> file-path drop-last vec)]
     (mk-node dir-path [file] complete-path))))

(defn attach [node paths complete-path]
  (let [p (first paths)
        remaining-paths (-> paths rest vec)]
    (cond
      (empty? node) (mk-node paths complete-path)
      (map? node) (if (contains? node p)
                    (let [sub-node (node p)
                          sub-node (attach sub-node remaining-paths complete-path)]
                      (if (vector? sub-node)
                        (assoc node p sub-node)
                        (assoc node p [sub-node])))
                    (assoc node p [(mk-node complete-path complete-path)]))
      (vector? node) (if (empty? remaining-paths)
                       (conj node {:name p
                                   :dir-path (-> complete-path drop-last vec)})
                       (let [vector-of-nodes node
                             found-node (->> vector-of-nodes
                                             (filter #(contains? % p))
                                             first)
                             without-found-node (->> vector-of-nodes
                                                     (remove #(contains? % p))
                                                     vec)]
                         (if found-node
                           (let [sub-node (found-node p)
                                 new-sub-node (attach sub-node remaining-paths complete-path)]
                             (if (vector? new-sub-node)
                               (conj without-found-node
                                     (assoc found-node p
                                            new-sub-node))
                               (assoc found-node p
                                      [new-sub-node])))
                           (let [n (mk-node paths complete-path)]
                             (conj node n)))))
      :else node)))

(defn mk-project-tree [files]
  (let [paths (mapv (fn [f]
                      (-> f (str/split #"/") rest vec))
                    files)]
    (reduce (fn [acc p]
              (attach acc p p))
            {}
            paths)))

(comment
  (walk-dir {:dir "/"
             :on-file (fn [file]
                        (prn file))})

  (a/go
    (prn (a/<! (ls "/foo"))))
  )
