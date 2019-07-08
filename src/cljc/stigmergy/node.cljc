(ns stigmergy.node
  (:require [stigmergy.tily :as tily]))

(defn dir? [f-or-d]
  (and (= 1 (count f-or-d))
       (-> f-or-d ffirst string?)))

(defn file? [f-or-d]
  (tily/some-in? :file/name (keys f-or-d)))

(defn ->path [f]
  (-> f (clojure.string/split #"/") rest vec))

(defn ->node-helper [full-path paths node ]
  (cond
    (empty? paths) node
    (= 2 (count paths)) (let [[path file] paths
                              file-parent-path (-> full-path drop-last vec)
                              n (assoc node path [{:file/name file
                                                   :parent file-parent-path}])
                              parent-path (vec (remove (fn [p]
                                                         (tily/some-in? p paths))
                                                       full-path))]
                          (assoc n :parent parent-path))
    :else (let [p (first paths)
                n (assoc node
                         p
                         [(->node-helper full-path (rest paths) {})])
                parent-path (vec (remove (fn [p]
                                           (tily/some-in? p paths))
                                         full-path))]
            (if (empty? parent-path)
              n
              (assoc n :parent parent-path)))))

(defn ->node [paths]
  (->node-helper paths paths {}))
(clojure.set/intersection #{1 2} #{2 3})

(defn index-of [nodes k]
  (->> (tily/with-index nodes)
       (filter (fn [[i node]]
                 (contains? node k)))
       ffirst))

(defn join-node [a b]
  (prn "a=" a)
  (prn "b=" b)
  (if (and (sequential? a) (map? b))
    (let [k (-> b (dissoc :parent) ffirst)
          i (index-of a k)
          c (a i)
          d (join-node c b)]
      (prn "k=" k " i=" i)
      (-> (tily/drop-nth a i)
          (tily/insert-at i d)))
    (let [a-keys (keys a)
          b-keys (keys b)
          _ (prn "a-keys=" a-keys)
          _ (prn "b-keys=" b-keys)
          common-keys (clojure.set/intersection (set a-keys) (set b-keys))
          common-keys (disj common-keys :parent :file/name)]
      (prn "common-keys=" common-keys)
      (cond
        (empty? common-keys) [a b]

        :else (let [ab (merge a b)
                    ab2 (into {} (for [ck common-keys
                                       :let [av (a ck)
                                             bv (b ck)]
                                       :when (not= av bv)]
                                   (cond
                                     (and (map? av) (map? bv)) [ck (join-node av bv)]
                                     (and (sequential? av)
                                          (sequential? bv)) (let [av-bv (into av bv)]
                                                              (prn "av-bv=" av-bv)
                                                              [ck  (reduce join-node
                                                                           av-bv)]
                                                              )
                                     :else [ck (conj [av] bv)])))]
                (merge ab ab2))))))

(comment
  (def nodes [{"scramblies" [{"resources" [{"public" [{:file/name "index.html"
                                                       :parent ["scramblies" "resources" "public"]}]
                                            :parent ["scramblies" "resources"]}]
                              :parent ["scramblies"]}]}
              {"scramblies" [{"src" [{"clj" [{"scramblies" [{:file/name "core.clj"
                                                             :parent ["scramblies" "src" "clj" "scramblies"]}]
                                              :parent ["src" "clj"]}]
                                      :parent ["scrambles" "src"]}]
                              :parent ["scramblies"]
                              }]}
              #_{"scramblies" [{"src" [{"clj" [{"scramblies" [{:file/name "server.clj"
                                                               :parent ["scramblies" "src" "clj" "scramblies"]}]
                                                :parent ["scramblies" "src" "clj"]}]
                                        :parent ["scramblies" "src"]}]}]}
              #_{"scramblies"
                 [{"src"
                   [{"clj"
                     [{:file/name "user.clj"
                       :parent ["scramblies" "src" "clj"]}]
                     :parent ["scramblies" "src"]}]
                   :parent ["scramblies"]}]}
              ])
  
  (reduce join-node nodes)

  (def x {"scramblies"
          [{"resources"
            [{"public"
              [{:file/name "index.html",
                :parent ["scramblies" "resources" "public"]}],
              :parent ["scramblies" "resources"]}],
            :parent ["scramblies"]}
           {"src"
            [{"clj"
              [{"scramblies"
                [{:file/name "core.clj",
                  :parent ["scramblies" "src" "clj" "scramblies"]}],
                :parent ["src" "clj"]}],
              :parent ["src"]}],
            :parent ["scramblies"]}]})

  (def y {"scramblies" [{"src" [{"clj" [{"scramblies" [{:file/name "server.clj"
                                                        :parent ["scramblies" "src" "clj" "scramblies"]}]
                                         :parent ["scramblies" "src" "clj"]}]
                                 :parent ["scramblies" "src"]}]}]})

  (join-node x y)

  
  {"scramblies"
   [{"resources"
     [{"public"
       [{:file/name "index.html",
         :parent ["scramblies" "resources" "public"]}],
       :parent ["scramblies" "resources"]}],
     :parent ["scramblies"]}
    {"src"
     {"clj"
      {"scramblies"
       {:file/name ["core.clj" "server.clj"],
        :parent ["scramblies" "src" "clj" "scramblies"]},
       :parent ["scramblies" "src" "clj"]},
      :parent ["scramblies" "src"]},
     :parent ["scramblies"]}]}
  )
