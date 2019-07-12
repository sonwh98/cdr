(ns stigmergy.cdr.fs
  (:require [clojure.string :as str]
            [clojure.set :as s]
            [clojure.data :as d]
            [clojure.core.async :as a :include-macros true]
            [cljs.pprint :as pp]
            [cljs-await.core :refer [await]]
            [taoensso.timbre :as log :include-macros true]
            
            [stigmergy.tily :as utily]
            [stigmergy.tily.js :as util]
            [stigmergy.cdr.node :as n]))

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

(defn write-file [filepath data]
  (a/go
    (a/<! (await (js/window.pfs.writeFile filepath data)))))

(defn read-file [filepath]
  (a/go
    (let [[err data] (a/<! (await (js/window.pfs.readFile filepath)))]
      (if err
        err
        data))))

;; (defn file? [f-or-d]
;;   (utily/some-in? :name (keys f-or-d)))

;; (defn dir? [f-or-d]
;;   (and (= 1 (count f-or-d))
;;        (-> f-or-d ffirst string?)))

(defn rm [file-path]
  (a/go
    (log/info "rm " file-path)
    (js/window.pfs.unlink file-path)))

#_(defn mk-tree
    "builds a tree by parsing a the string structure of a file path"
    [files]
    (let [nodes (mapv #(-> % ->path ->node)
                      files)]
      (prn nodes)
      #_(reduce join-node nodes)
      ))

(defn mk-node [file]
  (-> file n/->path n/->node))

(defn mk-dir-tree
  "builds a tree given the directory name"
  [dir]
  (a/go (let [files (atom [])]
          (a/<! (walk-dir {:dir dir
                           :on-file (fn [file]
                                      (when-not (re-find #".git" file)
                                        (swap! files conj file)))}))
          (let [nodes (mapv mk-node
                            @files)]
            (reduce n/join-node nodes)))))

(comment
  (def files ["/scramblies/resources/public/index.html" "/scramblies/src/clj/scramblies/core.clj" "/scramblies/src/clj/scramblies/server.clj" "/scramblies/src/clj/user.clj" "/scramblies/src/cljs/scramblies/core.cljs" "/scramblies/test/scramblies/tests.clj" "/scramblies/README.md" "/scramblies/project.clj"])

  (def files [;;"/scramblies/resources/public/index.html"
              "/scramblies/src/clj/scramblies/core.clj"
              "/scramblies/src/clj/scramblies/server.clj"])
  
  (def t  (mk-tree files))

  (def nodes
    [{"scramblies"
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
            :parent ["scramblies" "src" "clj"]}],
          :parent ["scramblies" "src"]}],
        :parent ["scramblies"]}]}
     {"scramblies"
      [{"src"
        [{"clj"
          [{"scramblies"
            [{:file/name "server.clj",
              :parent ["scramblies" "src" "clj" "scramblies"]}],
            :parent ["scramblies" "src" "clj"]}],
          :parent ["scramblies" "src"]}],
        :parent ["scramblies"]}]}
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
            :parent ["scramblies" "src" "cljs"]}],
          :parent ["scramblies" "src"]}],
        :parent ["scramblies"]}]}
     {"scramblies"
      [{"test"
        [{"scramblies"
          [{:file/name "tests.clj",
            :parent ["scramblies" "test" "scramblies"]}],
          :parent ["scramblies" "test"]}],
        :parent ["scramblies"]}]}
     {"scramblies" [{:file/name "README.md", :parent ["scramblies"]}]}
     {"scramblies"
      [{:file/name "project.clj", :parent ["scramblies"]}]}])

  (reduce n/join-node nodes)
  )
