(ns user
  (:require [figwheel-sidecar.repl-api :as f]
            [stigmergy.cdr.server :as s]
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


(comment
  (require '[stigmergy.tily :as tily])
  (def files [;;"/scramblies/resources/public/index.html"
              "/scramblies/src/clj/core.clj"
              "/scramblies/src/clj/server.clj"])
  
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
                                                        files? (some #(contains? % :file/name)
                                                                     av-bv)]
                                                    (if files?
                                                      [ck av-bv]
                                                      [ck [(reduce join-node
                                                                   av-bv)]]))
                           :else [ck (conj [av] bv)])))]
      (merge ab ab2)))

  (join-node {:a 1 :b {:src [1 2]} :d 10} {:a 2 :b {:src [3]} :c 1} )
  
  (let [path (-> "/foo" ->path )]
    (->node path)
    )


  (join-node {:a 1 :b 3} {:a 2 :c 10})

  (let [nodes (mapv #(-> % ->path ->node)
                    files)]
    nodes
    )

  (def a {"scramblies"
          [{"src"
            [{"clj"
              [{:file/name "core.clj", :parent ["scramblies" "src" "clj"]}],
              :parent ["scramblies" "src"]}],
            :parent ["scramblies"]}]})

  [{:file/name "core.clj" :parent ["scramblies" "src" "clj"]}
   {:file/name "server.clj" :parent ["scramblies" "src" "clj"]}]

  
  (def b {"scramblies"
          [{"src"
            [{"clj"
              [{:file/name "server.clj",
                :parent ["scramblies" "src" "clj"]}],
              :parent ["scramblies" "src"]}],
            :parent ["scramblies"]}]})

  (join-node a b)

  (some #(contains? % :file/name2)
        
        [{:file/name2 "core.clj", :parent ["scramblies" "src" "clj"]}
         {:file/name2 "server.clj", :parent ["scramblies" "src" "clj"]}])
  
  )
