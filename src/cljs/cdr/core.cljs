(ns cdr.core
  (:require [clojure.core.async :as a :include-macros true]
            [cljs.js :as cljs] 
            [com.kaicode.wocket.client :as ws :refer [process-msg]]
            [taoensso.timbre :as log :include-macros true]
            [reagent.core :as r]
            [cdr.mdc :as mdc]
            ;;[stigmergy.mr-clean :as r]
            [taoensso.timbre :as log :include-macros true]
            [cljs-await.core :refer [await]]
            [clojure.string :as str]
            ))

(def app-state (r/atom {:code-text ""
                        :repl-text ""
                        :current-ns 'cljs.user}))

(def current-ns (r/cursor app-state [:current-ns]))
(def cljs-state (cljs.js/empty-state))

(defn array-buffer->str [array-buffer]
  (let [decoder (js/TextDecoder. "UTF-8")]
    (.. decoder (decode array-buffer))))

(defn obj->clj
  [obj]
  (if (goog.isObject obj)
    (-> (fn [result key]
          (let [v (goog.object/get obj key)]
            (if (= "function" (goog/typeOf v))
              result
              (assoc result key (obj->clj v)))))
        (reduce {} (.getKeys goog/object obj)))
    obj))

(defn process-ns! [s-expression]
  (let [ns-form? #(and (list? %)
                       (= 'ns (first %)))
        ns-form (or
                 (if (ns-form? s-expression)
                   s-expression
                   nil)
                 (some->> s-expression
                          (filter ns-form?)
                          first))
        ns-symbol (second ns-form)]
    (when ns-symbol
      (cljs.js/eval cljs-state ns-form {:eval cljs.js/js-eval
                                        :ns @current-ns
                                        :def-emits-var true
                                        :verbose true}
                    (fn [a-map]
                      (when (nil? (-> a-map :error :value))
                        (reset! current-ns ns-symbol)))))
    (remove ns-form? s-expression)))

(def async-eval (let [c (a/chan)]
                  (fn [s-expression]
                    (let [s-expression (process-ns! s-expression)]
                      (cljs.js/eval cljs-state s-expression {:eval cljs.js/js-eval
                                                             :ns @current-ns
                                                             :def-emits-var true
                                                             :verbose true}
                                    (fn [a-map]
                                      (log/info (keys a-map))
                                      (if-let [value (:value a-map)]
                                        (do (log/info a-map)
                                            (a/put! c value))
                                        (let [error {:error a-map}]
                                          (log/info error)
                                          )
                                        ))))
                    c)))

(def walk-dir (fn [{:keys [dir on-file] :as params}]
                (a/go
                  (let [[err files] (a/<! (await (js/window.pfs.readdir dir)))]
                    (doseq [f files]
                      (let [f-full-path (str dir "/" f)
                            [err stat] (a/<! (await (js/window.pfs.stat f-full-path)))
                            stat (obj->clj stat)]
                        (if (= (stat "type") "dir")
                          (a/<! (walk-dir (merge params
                                                 {:dir f-full-path})))
                          (when on-file
                            (on-file f-full-path)))))))))


(comment
  (a/go (let [;;expr '(ns foo.bar)
              expr '(defn hello [n] (str "hello " n))
              r (async-eval expr)]
          (prn "r=" (a/<! r))))

  (-> @cljs-state keys)
  (-> @cljs-state :cljs.analyzer/namespaces keys)
  
  )

(defn code-area [state]
  (let [code-text (r/cursor state [:code-text])
        codemirror (atom nil)]
    (r/create-class
     {:component-did-mount (fn [this]
                             (let [editor (js/document.getElementById "editor")
                                   cm (js/CodeMirror.fromTextArea editor #js{:lineNumbers true
                                                                             :mode "text/x-clojure"
                                                                             :autoCloseBrackets true
                                                                             :matchBrackets true
                                                                             :theme "dracula"
                                                                             :keyMap "emacs"
                                                                             })]
                               (reset! codemirror cm)
                               (js/parinferCodeMirror.init cm)))
      :reagent-render (fn [state]
                        [:div 
                         [:textarea#editor {:style {:width "100%"
                                                    :height 200}}]
                         [mdc/button {:on-click #(a/go (let [txt (.. @codemirror getValue)
                                                             s-expression (cljs.reader/read-string
                                                                           (str "(do " txt ")"))
                                                             r (a/<! (async-eval s-expression))]
                                                         (prn s-expression)
                                                         (prn "r=" r)))}
                          "Eval"]
                         [mdc/button {:on-click #(reset! code-text "")}
                          "Clear"]])})))

(defn repl-area [state]
  (let [repl-text (r/cursor state [:repl-text])]
    [:div
     [:textarea {:style {:width "100%"
                         :height 200}
                 :value @repl-text
                 :read-only true}]
     [:button "Clear REPL"]]))

(defn stop-propagation [evt]
  (. evt stopPropagation)  )

(defn stop-prevent [evt]
  (. evt preventDefault)
  (. evt stopPropagation))

(defn tree-node-view [{:keys [label on-click] :as node} ]
  (let [children (->> node :node/children
                      (map (fn [node] [tree-node-view node])))
        id (:node/name node)]
    [:li {:class "li-tree"}
     [:label {:class "label-tree" :for id
              :on-click (fn [evt]
                          (on-click node)
                          (stop-prevent evt))} label]
     [:input {:class "input-tree" :type "checkbox" :id id
              :on-click stop-propagation}]
     (into  [:ol {:class "ol-tree"
                  :on-click stop-propagation}] children)]))

(defn tree [{:keys [root on-click]}]
  [:aside 
   (into [:ol {:class "tree"}]
         (for [node (:node/children @root)]
           [tree-node-view node on-click]))])

(defn file-item [file]
  [:a {:class "mdc-list-item " :tabIndex 0
       :aria-selected "true"
       :on-click #(let [cm (js/document.querySelector ".CodeMirror")
                        cm (.. cm -CodeMirror)]
                    (a/go
                      (let [[err file-content] (a/<! (await (js/window.pfs.readFile file)))
                            file-content (array-buffer->str file-content)]
                        (.. cm getDoc (setValue file-content)))))}
   [:i {:class "material-icons mdc-list-item__graphic" :aria-hidden "true"} "bookmark"]
   file])

(defn attach-node-to-parent [parent-node child-node]
  (if-let [first-child (some-> parent-node :node/children first)]
    (assoc parent-node :node/children [(attach-node-to-parent first-child child-node)])
    (assoc parent-node :node/children  [child-node])))

(defn mkdir [paths]
  (let [paths (rest (str/split paths #"/"))]
    (reduce (fn [root-node path]
              (if (empty? root-node)
                {:node/name path
                 :node/children []}
                (attach-node-to-parent root-node {:node/name path})))
            {}
            paths)))


(defn mk-tree [dir-path file-names]
  (let [paths (rest (str/split dir-path #"/"))
        last-path (last paths)]
    (reduce (fn [root-node path]
              (if (empty? root-node)
                {:node/name path
                 :node/children []}
                (if (= path last-path)
                  (attach-node-to-parent root-node {:node/name path
                                                    :node/children file-names})
                  (attach-node-to-parent root-node {:node/name path}))))
            {}
            paths)))

(defn node->path-helper [node path]
  (if (string? node)
    path
    (let [children (:node/children node)
          path (str path "/" (:node/name node))]
      (if (> 1 (count children))
        path
        (node->path-helper (first children) path)))))

(defn node->path [node]
  (node->path-helper node ""))

(defn merge-nodes [& nodes]
  (let [merge-fn (fn [a b]
                   (if (= a b)
                     a
                     (if (and (vector? a)
                              (vector? b))
                       (update-in a [0 :node/children] into (-> b first :node/children))
                       b)))]
    (apply merge-with (into [merge-fn] nodes))))

(comment
  (mk-tree "/cdr/src/cljs/cdr" ["core.cljs" "test.cljs" "util.cljs"])
  
  (node->path {:node/name "a"
               :node/children [{:node/name "b"
                                :node/children [{:node/name "c"}]}]})

  (merge-nodes {:node/name "cdr"
                :node/children [{:node/name "src"
                                 :node/children [{:node/name "clj"
                                                  :node/children [{:node/name "cdr"
                                                                   :node/children ["server.clj"]}]}]}]}
               {:node/name "cdr"
                :node/children [{:node/name "src"
                                 :node/children [{:node/name "cljs"
                                                  :node/children [{:node/name "cdr"
                                                                   :node/children ["core.cljs" "mdc.cljs"]}]}]}]}
               {:node/name "cdr"
                :node/children [{:node/name "resources"
                                 :node/children [{:node/name "public"
                                                  :node/children [{:node/name "js"
                                                                   :node/children ["cdr.js" "codemirrror.js"]}
                                                                  {:node/name "css"
                                                                   :node/children ["codemirrror.css"
                                                                                   "clojure.css"]}]}]}]}

               )
  
  (merge-with (fn [a b]
                (prn "a=" a)
                (prn "b=" b)
                (if-not (= a b)
                  (if (and (vector? a)
                           (vector? b))
                    (update-in a [0 :node/children] conj (-> b first :node/children))
                    b)
                  a))
              {:node/name "cdr"
               :node/children [{:node/name "src"
                                :node/children [{:node/name "core.cljs"}]}]}
              {:node/name "cdr"
               :node/children [{:node/name "src"
                                :node/children [{:node/name "core2.cljs"}]}]}
              
              )


  )

(defn git-clone [{:keys [url dir]}]
  (a/go
    (a/<! (await (js/window.pfs.mkdir dir)))
    (a/<! (await (js/git.clone #js{:dir dir
                                   :corsProxy "https://cors.isomorphic-git.org"
                                   :url url
                                   :ref "master"
                                   :singleBranch true
                                   :depth 10})))))

(defn git-input [project-name]
  (let [value (r/atom "https://github.com/sonwh98/cdr.git")]
    (fn [project-name]
      [:div
       [:input {:type :text
                :placeholder "git URL"
                :style {:width "100%"}
                :value @value
                :on-change (fn [evt]
                             (reset! value (-> evt .-target .-value)))}]
       [:div.mdc-button {:on-click (fn [evt]
                                     (let [git-url @value
                                           repo-name (some-> git-url
                                                             (str/split "/")
                                                             last
                                                             (str/replace ".git" ""))
                                           dir (str "/" repo-name)
                                           dir->files (atom {})
                                           root (atom {:name "/"
                                                       :children []})]
                                       (reset! project-name repo-name)
                                       (a/go
                                         (a/<! (git-clone {:url git-url
                                                           :dir dir}))
                                         (a/<! (walk-dir {:dir dir
                                                          :on-file (fn [file]
                                                                     (when-not (re-find #".git" file)
                                                                       (let [paths (str/split file #"/")
                                                                             paths (rest paths)
                                                                             dir (->>  paths
                                                                                       butlast
                                                                                       (str/join "/"))
                                                                             file (last paths)]
                                                                         (swap! dir->files
                                                                                update-in [dir] conj file))
                                                                       ))}))
                                         (doseq [[dir files] @dir->files]
                                           (prn (mk-tree dir files))
                                           )
                                         
                                         (let [project-files (->> @dir->files vals  flatten)]
                                           (swap! app-state assoc-in [:files] project-files))
                                         )))} "GET"]])))

(defn cdr-ui [state]
  (r/create-class {:component-did-mount (fn [component]
                                          (when-let [project-name (:project-name @state)]
                                            (walk-dir {:dir (str "/" project-name)
                                                       :on-file (fn [file]
                                                                  (if-not (re-find #".git" file)
                                                                    (swap! state update-in [:files] conj file))
                                                                  )}))               
                                          )
                   
                   :reagent-render (fn [state]
                                     [:div
                                      (let [project-name (r/cursor state [:project-name])]
                                        [mdc/drawer {:drawer-header [:div
                                                                     [:h3 {:class "mdc-drawer__title"} "Project"]
                                                                     [:h6 {:class "mdc-drawer__subtitle"} @project-name]
                                                                     [git-input project-name]
                                                                     
                                                                     ]
                                                     :content [code-area state]
                                                     :drawer-content (for [file (-> @state :files)]
                                                                       ^{:key file} [file-item file])}])
                                      #_[mdc/tab-bar]
                                      ])}))

(defn init []
  (set! (.-fs js/window) (js/LightningFS. "fs"))
  (js/git.plugins.set "fs" (.-fs js/window))
  (set! (.-pfs js/window)  js/window.fs.promises)

  
  (ws/connect-to-websocket-server {:port 3000})
  (r/render-component [cdr-ui app-state] (js/document.getElementById "app"))
  (log/set-level! :info))

(defmethod process-msg :chat-broadcast [[_ msg]]
  (prn "from clj " msg)
  (swap! app-state assoc :repl-text msg))


(init)



(comment
  (a/go
    (let [dir "/cdr2"]
      (prn "1->" (a/<! (await (js/window.pfs.mkdir dir))))
      (prn "2->" (a/<! (await (js/window.pfs.readdir dir))))
      (prn "3->" (a/<! (await (js/git.clone #js{:dir "/cdr2"
                                                :corsProxy "https://cors.isomorphic-git.org"
                                                :url "https://github.com/sonwh98/cdr.git"
                                                :ref "master"
                                                :singleBranch true
                                                :depth 10}))))
      (prn "4->" (a/<! (await (js/window.pfs.readdir dir))))
      (prn "4->" (a/<! (await (js/window.pfs.readFile (str dir "/src/clj/user.clj")))))
      ))

  (a/go
    (prn (a/<! (await (js/git.log #js{:dir "/cdr2"}))))
    (let [[err buff] (a/<! (await (js/window.pfs.readFile "/cdr2/src/clj/user.clj")))]
      (prn "user.clj=" (array-buffer->str  buff ) ))
    )

  (a/go
    (let [[err files] (a/<! (await (js/git.listFiles #js{:dir "/cdr2/"
                                                         :ref "HEAD"})))]
      (prn err)
      (prn files)
      ))


  (git-clone {:url "https://github.com/sonwh98/cdr.git"
              :dir "/cdr" })
  
  (walk-dir {:dir "/cdr"
             :on-file (fn [files]
                        (prn "f=" files)
                        )})


  
  (walk-dir {:dir (str "/" )
             :on-file (fn [file]
                        (let [file (str/replace file #"/cdr2/" "")]
                          (if-not (re-find #".git" file)
                            (swap! app-state update-in [:files] conj file)))
                        )})

  (defn in? [e coll]
    (boolean (some #(= % e) coll)))
  
  
  (mkdir "cdr/src/util/core.cljs")
  
  )
