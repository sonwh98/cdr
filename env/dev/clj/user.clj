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
  
  (defn join-node [a b]
    
    )
  
  (let [path (-> "/foo" ->path )]
    (->node path)
    )


  
  (let [path (-> (files 0)
                 ->path)
        node (->node path) ]
    node
    )

  
  )
