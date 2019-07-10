(ns stigmergy.node
  (:require [stigmergy.tily :as tily]))

(defn dir? [f-or-d]
  (and (= 1 (count f-or-d))
       (-> f-or-d ffirst string?)))

(defn file? [f-or-d]
  (tily/some-in? :file/name (keys f-or-d)))

(defn ->path [f]
  (-> f (clojure.string/split #"/") rest vec))

(defn get-parent-path [full-path paths]
  (->> full-path (drop-last (count paths)) vec))

(defn ->node-helper [full-path paths node]
  (prn "full-path=" full-path " paths=" paths)
  (prn "node=" node)
  (cond
    (empty? paths) node
    (= 2 (count paths)) (let [[path file] paths
                              file-parent-path (-> full-path drop-last vec)
                              n (assoc node path [{:file/name file
                                                   :parent file-parent-path}])
                              parent-path (get-parent-path full-path paths)]
                          (prn "parent-path1=" parent-path)
                          (prn "n=" n)
                          (if (empty? parent-path)
                            n
                            (assoc n :parent parent-path)))
    :else (let [p (first paths)
                n (assoc node
                         p
                         [(->node-helper full-path (rest paths) {})])
                parent-path (get-parent-path full-path paths)]
            (prn "parent-path2=" parent-path)
            (if (empty? parent-path)
              n
              (let [n2 (assoc n :parent parent-path)]
                (prn "n2=" n2)

                n2)))))

(defn ->node [paths]
  (->node-helper paths paths {}))

(clojure.set/intersection #{1 2} #{2 3})

(defn index-of [nodes k]
  (->> (tily/with-index nodes)
       (filter (fn [[i node]]
                 (contains? node k)))
       ffirst))

(defn join-node [a b]
  ;; (prn "a=" a)
  ;; (prn "b=" b)
  (cond
    (and (sequential? a) (map? b)) (let [k (-> b (dissoc :parent :file/name) ffirst)
                                         i (index-of a k)]
                                     ;;(prn "k=" k " i=" i)
                                     (if i
                                       (let [c (a i)
                                             d (join-node c b)
                                             e (-> (tily/drop-nth a i)
                                                   (tily/insert-at i d))]
                                         ;; (prn "c=" c)
                                         ;; (prn "d=" d)
                                         ;; (prn "e=" e)
                                         e)
                                       (let [r (into a [b])]
                                         ;; (prn "a2=" a)
                                         ;; (prn "b2=" b)
                                         ;; (prn "r=" r)
                                         r)))
    (and (map? a) (map? b)) (let [a-keys (keys a)
                                  b-keys (keys b)
                                  ;; _ (prn "a-keys=" a-keys)
                                  ;; _ (prn "b-keys=" b-keys)
                                  common-keys (clojure.set/intersection (set a-keys) (set b-keys))
                                  common-keys (disj common-keys :parent :file/name)]
                              ;;(prn "common-keys=" common-keys)
                              (if (empty? common-keys)
                                [a b]
                                (let [ab (merge a b)
                                      ab2 (into {} (for [ck common-keys
                                                         :let [av (a ck)
                                                               bv (b ck)]
                                                         :when (not= av bv)]
                                                     (cond
                                                       (and (map? av) (map? bv)) [ck (join-node av bv)]
                                                       (and (sequential? av)
                                                            (sequential? bv)) (let [av-bv (into av bv)
                                                                                    joined (reduce join-node
                                                                                                   av-bv)]
                                                                                ;; (prn "av-bv=" av-bv)
                                                                                ;; (prn "joined=" joined)
                                                                                (if (sequential? joined)
                                                                                  [ck  joined]
                                                                                  [ck [joined]]))
                                                       :else [ck (conj [av] bv)])))]
                                  (merge ab ab2))))
    :else (prn "error")))

(comment
  (def files ["/scramblies/resources/public/index.html" "/scramblies/src/clj/scramblies/core.clj" "/scramblies/src/clj/scramblies/server.clj" "/scramblies/src/clj/user.clj" "/scramblies/src/cljs/scramblies/core.cljs" "/scramblies/test/scramblies/tests.clj" "/scramblies/README.md" "/scramblies/project.clj"])

  (-> "/scramblies/src/cljs/scramblies/core.cljs"
      ->path ->node)

  (def nodes (mapv (fn [n]
                     (-> n ->path ->node)) files))
  (reduce join-node nodes)
  )
