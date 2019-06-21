(ns stigmergy.cdr.dir-navigator
  (:require [stigmergy.tily :as tily]
            [reagent.core :as r]
            [taoensso.timbre :as log :include-macros true]))

(defn toggle [node evt]
  (let [element (.-target evt)
        parent (.-parentElement element)]
    (swap! node update :visible? not)
    (.. parent (querySelector ".sub-dir") -classList (toggle "active"))))

(defn get-name [node]
  (-> node keys first))

(defn get-children [node]
  (-> node vals first))

(defn dir [node on-click]
  [:li
   [:span {:class "dir"
           :on-click #(toggle node %)} (get-name @node)]
   [:ul {:class (if (:visible? @node)
                  "sub-dir active"
                  "sub-dir")
         :style {:list-style-type :none}}
    (let [index-children (-> @node get-children tily/with-index)]
      (doall (for [[index c] index-children
                   :let [k (-> @node keys first)
                         child (r/cursor node [k index])]]
               (with-meta (if-let [file-name (:name c)]
                            [:li {:on-click #(on-click c)
                                  :on-context-menu #(do
                                                      (.. % preventDefault)
                                                      (js/alert "right"))} file-name]
                            [dir child on-click])
                 {:key (str c)}))))]])

#_(defn tree [{:keys [node on-click] :as args}]
    (prn "tree " node)
    (when-not (empty? @node)
      [:ul {:style {:list-style-type :none
                    :overflow :auto
                    :margin 0
                    :padding 0}}
       [dir node on-click]]))

(defn tree2 [{:keys [node on-click] :as args}]
  (when-not (empty? @node)
    [:ul {:style {:list-style-type :none
                  :overflow :auto
                  :margin 0
                  :padding 0}}
     [dir node on-click]]))

(comment
  {"cdr"
   [{"env"
     [{"dev"
       [{"clj" [{:name "user.clj", :dir-path ["cdr" "env" "dev" "clj"]}]}
        {"cljs"
         [{"stigmergy"
           [{"cdr"
             [{:name "init.cljs",
               :dir-path
               ["cdr" "env" "dev" "cljs" "stigmergy" "cdr"]}]}]}]}]}]}
    {"resources"
     [{"public"
       [{"css"
         [{:name "codemirror.css",
           :dir-path ["cdr" "resources" "public" "css"]}
          {:name "docs.css",
           :dir-path ["cdr" "resources" "public" "css"]}
          {:name "dracula.css",
           :dir-path ["cdr" "resources" "public" "css"]}
          {:name "material-components-web.min.css",
           :dir-path ["cdr" "resources" "public" "css"]}
          {:name "tree.css",
           :dir-path ["cdr" "resources" "public" "css"]}]}
        {"js"
         [{"keymap"
           [{:name "emacs.js",
             :dir-path ["cdr" "resources" "public" "js" "keymap"]}]}
          {:name "active-line.js",
           :dir-path ["cdr" "resources" "public" "js"]}
          {:name "clojure-parinfer.js",
           :dir-path ["cdr" "resources" "public" "js"]}
          {:name "clojure.js",
           :dir-path ["cdr" "resources" "public" "js"]}
          {:name "closebrackets.js",
           :dir-path ["cdr" "resources" "public" "js"]}
          {:name "codemirror.js",
           :dir-path ["cdr" "resources" "public" "js"]}
          {:name "lightning-fs.min.js",
           :dir-path ["cdr" "resources" "public" "js"]}
          {:name "matchbrackets.js",
           :dir-path ["cdr" "resources" "public" "js"]}
          {:name "material-components-web.min.js",
           :dir-path ["cdr" "resources" "public" "js"]}
          {:name "parinfer-codemirror.js",
           :dir-path ["cdr" "resources" "public" "js"]}
          {:name "parinfer.js",
           :dir-path ["cdr" "resources" "public" "js"]}]}
        {:name "index.html", :dir-path ["cdr" "resources" "public"]}]}]}
    {"src"
     [{"clj"
       [{"stigmergy"
         [{"cdr"
           [{:name "server.clj",
             :dir-path ["cdr" "src" "clj" "stigmergy" "cdr"]}]}]}]}
      {"cljs"
       [{"stigmergy"
         [{"cdr"
           [{:name "dir_navigator.cljs",
             :dir-path ["cdr" "src" "cljs" "stigmergy" "cdr"]}
            {:name "fs.cljs",
             :dir-path ["cdr" "src" "cljs" "stigmergy" "cdr"]}
            {:name "mdc.cljs",
             :dir-path ["cdr" "src" "cljs" "stigmergy" "cdr"]}
            {:name "util.cljs",
             :dir-path ["cdr" "src" "cljs" "stigmergy" "cdr"]}
            {:name "core.cljs",
             :dir-path ["cdr" "src" "cljs" "stigmergy" "cdr"]}]}]}]}]}
    {:name "Dockerfile", :dir-path ["cdr"]}
    {:name "build.sh", :dir-path ["cdr"]}
    {:name "README.md", :dir-path ["cdr"]}
    {:name "project.clj", :dir-path ["cdr"]}]}
  )
