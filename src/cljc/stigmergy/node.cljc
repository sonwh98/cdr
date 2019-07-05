(ns stigmergy.node
  (:require [stigmergy.tily :as tily]))

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

(defn join-node [a b]
  (let [a-keys (keys a)
        b-keys (keys b)
        common-keys (clojure.set/intersection (set a-keys) (set b-keys))
        ab (merge a b)
        ab2 (into {} (for [ck common-keys
                           :let [av (a ck)
                                 bv (b ck)]
                           :when (not= av bv)]
                       (cond
                         (and (map? av) (map? bv)) [ck (join-node av bv)]
                         (and (sequential? av)
                              (sequential? bv)) (let [av-bv (into av bv)
                                                      _ (prn "av-bv=" av-bv)
                                                      files? (some (fn [e]
                                                                     (try
                                                                       (contains? e :file/name)
                                                                       (catch Exception ex
                                                                         (prn "e=" e)))

                                                                     )
                                                                   av-bv)]
                                                  (if files?
                                                    [ck av-bv]
                                                    [ck [(reduce join-node
                                                                 av-bv)]]))
                         :else [ck (conj [av] bv)])))]
    (merge ab ab2)))

(comment
  (def nodes [{"scramblies"
               [{"resources"
                 [{"public"
                   [{:file/name "index.html",
                     :parent ["scramblies" "resources" "public"]}],
                   :parent ["scramblies" "resources"]}],
                 :parent ["scramblies"]}]}
              {"scramblies"
               [{"src"
                 [{"clj"
                   [{"scramblies"
                     [{:file/name "core.clj",
                       :parent ["scramblies" "src" "clj" "scramblies"]}],
                     :parent ["src" "clj"]}],
                   :parent ["src"]}]}]}
              {"scramblies"
               [{"src"
                 [{"clj"
                   [{"scramblies"
                     [{:file/name "server.clj",
                       :parent ["scramblies" "src" "clj" "scramblies"]}],
                     :parent ["src" "clj"]}],
                   :parent ["src"]}]}]}
              {"scramblies"
               [{"src"
                 [{"clj"
                   [{:file/name "user.clj",
                     :parent ["scramblies" "src" "clj"]}],
                   :parent ["scramblies" "src"]}],
                 :parent ["scramblies"]}]}
              {"scramblies"
               [{"src"
                 [{"cljs"
                   [{"scramblies"
                     [{:file/name "core.cljs",
                       :parent ["scramblies" "src" "cljs" "scramblies"]}],
                     :parent ["src" "cljs"]}],
                   :parent ["src"]}]}]}
              {"scramblies"
               [{"test"
                 [{"scramblies"
                   [{:file/name "tests.clj",
                     :parent ["scramblies" "test" "scramblies"]}],
                   :parent ["test"]}]}]}
              {"scramblies" [{:file/name "README.md", :parent ["scramblies"]}],
               :parent []}
              {"scramblies"
               [{:file/name "project.clj", :parent ["scramblies"]}],
               :parent []}])
  
  (reduce join-node nodes)
  )
