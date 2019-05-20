(ns stigmergy.cdr.core
  (:require [clojure.core.async :as a :include-macros true]
            [cljs.js :as cljs] 
            [com.kaicode.wocket.client :as ws :refer [process-msg]]
            [taoensso.timbre :as log :include-macros true]
            [reagent.core :as r]
            [stigmergy.tily.js :as util]
            
            [stigmergy.cdr.mdc :as mdc]
            [stigmergy.cdr.fs :as fs]
            [stigmergy.cdr.git :as git]
            [stigmergy.cdr.dir-navigator :as dir]
            ;;[stigmergy.mr-clean :as r]

            [taoensso.timbre :as log :include-macros true]
            [cljs-await.core :refer [await]]
            [clojure.string :as str]
            [clojure.pprint :as pp]))

(def app-state (r/atom {:code-text ""
                        :repl-text ""
                        :current-ns 'cljs.user
                        :current-project "cdr"
                        :projects {"cdr" {:git {:url ""
                                                :username ""
                                                :password ""}
                                          :src-tree nil}}}))

(def current-ns (r/cursor app-state [:current-ns]))
(def cljs-state (cljs.js/empty-state))

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

(defn git-input [state]
  (let [value (r/atom "https://github.com/sonwh98/cdr.git")]
    (fn [state]
      (let [current-project (:current-project @state)]
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
                                             files (atom [])]

                                         ;;(reset! project-name repo-name)
                                         ;;(clojure.set/rename-keys @project )
                                         
                                         (a/go
                                           (a/<! (git/clone {:url git-url
                                                             :dir dir}))
                                           (a/<! (fs/walk-dir {:dir dir
                                                               :on-file (fn [file]
                                                                          (when-not (re-find #".git" file)
                                                                            (swap! files conj file)))}))
                                           (let [project-root (fs/mk-project-tree @files)]
                                             (swap! state assoc-in
                                                    [:projects current-project :src-tree]
                                                    project-root)
                                             ))))} "GET"]]))))

(defn file-manager [state]
  (let [current-project (:current-project @state)
        project (r/cursor state [:projects current-project])
        src-tree (r/cursor project [:src-tree])]
    [:div 
     [git-input state]
     [dir/tree {:node src-tree
                :on-click (fn [{:keys [name dir-path] :as file}]
                            (let [cm (js/document.querySelector ".CodeMirror")
                                  cm (.. cm -CodeMirror)
                                  dir-path (str/join "/" dir-path)
                                  file-name (str "/" dir-path "/" name)]
                              (a/go
                                (let [[err file-content] (a/<! (await (js/window.pfs.readFile file-name)))
                                      file-content (util/array-buffer->str file-content)]
                                  (.. cm getDoc (setValue file-content))))))
                }]]))

(defn left-panel [state]
  (let [hidden? (r/atom false)]
    (fn [state]
      (let [{:keys [width height]} (util/get-dimensions)
            half-height (- (/ height 2) 10)] 
        [:div {:style {:background-color :pink}}
         [:div {:style {:transform (util/format "translate(-49%, %dpx) rotate(-90deg)" half-height)
                        :display :grid
                        :grid-template-columns "auto auto" 
                        :background-color :green
                        :width height}}
          [:button {:style {:width "100%"}} "Structure"]
          [:button {:style {:width "100%"}
                    :on-click #(swap! hidden? not)} "Project"]]

         [:div {:style {:position :relative
                        :left 15}}
          (if @hidden?
            [:h1 "nothing to see here. move on along"]
            [file-manager state]
            )]])))
  )

(defn cdr-ui [state]
  (r/create-class
   {:component-did-mount
    (fn [component]
      #_(when-let [project-name (:project-name @state)]
          (fs/walk-dir {:dir (str "/" project-name)
                        :on-file (fn [file]
                                   (if-not (re-find #".git" file)
                                     (swap! state update-in [:files] conj file)))})))
    
    :reagent-render
    (fn [state]
      [:div
       [left-panel state]
       #_(let [project-name (r/cursor state [:project-name])
               project-root (r/cursor state [:project-root])]
           [mdc/drawer {:drawer-header [:div
                                        [:h3 {:class "mdc-drawer__title"} "Project"]
                                        [:h6 {:class "mdc-drawer__subtitle"} @project-name]
                                        [git-input project-name]]
                        :content [code-area state]
                        :drawer-content
                        [dir/tree {:node project-root
                                   :on-click (fn [{:keys [name dir-path] :as file}]
                                               (let [cm (js/document.querySelector ".CodeMirror")
                                                     cm (.. cm -CodeMirror)
                                                     dir-path (str/join "/" dir-path)
                                                     file-name (str "/" dir-path "/" name)]
                                                 (a/go
                                                   (let [[err file-content] (a/<! (await (js/window.pfs.readFile file-name)))
                                                         file-content (util/array-buffer->str file-content)]
                                                     (.. cm getDoc (setValue file-content))))))
                                   }]}])
       #_[mdc/tab-bar]
       ])}))

(defn init []
  (set! (.-fs js/window) (js/LightningFS. "fs"))
  (js/git.plugins.set "fs" (.-fs js/window))
  (set! (.-pfs js/window)  js/window.fs.promises)
  
  ;;(ws/connect-to-websocket-server {:port 80})
  (r/render-component [cdr-ui app-state] (js/document.getElementById "app"))
  (log/set-level! :info))

(defmethod process-msg :chat-broadcast [[_ msg]]
  (prn "from clj " msg)
  (swap! app-state assoc :repl-text msg))


