(ns stigmergy.cdr.core
  (:require [clojure.core.async :as a :include-macros true]
            [cljs.js :as cljs] 
            [com.kaicode.wocket.client :as ws :refer [process-msg]]
            [taoensso.timbre :as log :include-macros true]
            [reagent.core :as r]
            [reagent.dom :as dom]
            [stigmergy.tily.js :as util]
            [stigmergy.eve :as eve]
            [stigmergy.cdr.mdc :as mdc]
            [stigmergy.cdr.fs :as fs]
            [stigmergy.cdr.git :as git]
            [stigmergy.cdr.dir-navigator :as dir]
            ;;[stigmergy.mr-clean :as r]

            [taoensso.timbre :as log :include-macros true]
            [cljs-await.core :refer [await]]
            [clojure.string :as str]
            [clojure.pprint :as pp]))

#_(set! (.-hello (.-prototype js/HTMLElement)) (fn [] "hello"))

(def app-state (r/atom {:code-text ""
                        :repl-text ""
                        :current-ns 'cljs.user
                        :current-project "cdr"
                        :long-press-start? false
                        :projects {"cdr" {:git {:url ""
                                                :username ""
                                                :password ""}
                                          :src-tree nil}}
                        :project-manager {:visible? true
                                          :context-menu {:x 0 :y 0
                                                         :visible? false}}
                        :dialog {:visible? false
                                 :content nil}
                        }))

(defn hide-context-menu []
  (swap! app-state assoc-in [:project-manager :context-menu :visible?] false))

(defn show-context-menu [x y]
  (swap! app-state update-in
         [:project-manager
          :context-menu]
         (fn [context-menu]
           (-> context-menu
               (assoc :visible? true)
               (assoc :x x)
               (assoc :y y)))))

(defn menu-item [{:keys [label on-click]}]
  [:a {:href "#"
       :on-click #(do
                    (hide-context-menu)
                    (when on-click
                      (on-click %)))} label])

(defn show-dialog []
  (swap! app-state assoc-in [:dialog :visible?] true))

(defn hide-dialog []
  (swap! app-state assoc-in [:dialog :visible?] false))

(defn context-menu [context-menu-state]
  (when (:visible? @context-menu-state)
    [:div {:class "vertical-menu"
           :style {:position :absolute
                   :left (:x @context-menu-state)
                   :top (:y @context-menu-state)}}
     [menu-item {:label "checkout"
                 :on-click show-dialog}]
     [menu-item {:label "branch"
                 :on-click show-dialog}]
     [menu-item {:label "commit"}]
     [menu-item {:label "push"}]
     [menu-item {:label "reset"}]]))

(defn dialog [dialog-state]
  (when (:visible? @dialog-state)
    [:div {:id "myModal", :class "modal"
           :style {:display :block}}  
     [:div {:class "modal-content"}
      [:span {:class "close"
              :on-click hide-dialog} "×"]
      [:p "Some text in the Modal.."]]]))

(comment
  (swap! app-state assoc-in [:project-manager :context-menu :visible?] true)
  (swap! app-state update-in [:project-manager :context-menu :visible?] not)
  (swap! app-state update-in [:dialog :visible?] not)
  (-> @app-state :project-manager :context-menu)
  )

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

(defn toggle-project-manager-visibility []
  (swap! app-state update-in [:project-manager :visible?] not))

(defn close-project-manager []
  (swap! app-state assoc-in [:project-manager :visible?] false))

(defn code-area [state]
  (let [code-text (r/cursor state [:code-text])
        codemirror (atom nil)]
    (r/create-class
     {:component-did-mount (fn [this]
                             (let [{:keys [width height]} (util/get-dimensions)
                                   editor (js/document.getElementById "editor")
                                   cm (js/CodeMirror.fromTextArea editor #js{:lineNumbers true
                                                                             :mode "text/x-clojure"
                                                                             :autoCloseBrackets true
                                                                             :matchBrackets true
                                                                             :theme "dracula"
                                                                             :keyMap "emacs"
                                                                             })]
                               (.. cm (setSize nil height))
                               (reset! codemirror cm)
                               (js/parinferCodeMirror.init cm)))
      :reagent-render (fn [state]
                        (let [{:keys [width height]} (util/get-dimensions)]
                          [:div {:style {:position :absolute
                                         :left 20
                                         :width "100%"}
                                 :on-click close-project-manager}
                           [:textarea#editor]
                           [mdc/button {:on-click #(a/go (let [txt (.. @codemirror getValue)
                                                               s-expression (cljs.reader/read-string
                                                                             (str "(do " txt ")"))
                                                               r (a/<! (async-eval s-expression))]
                                                           (prn s-expression)
                                                           (prn "r=" r)))}
                            "Eval"]
                           ]))})))



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

(defn ts []
  (.getTime (js/Date.)))



(defn project-manager [state]
  (let [{:keys [width height]} (util/get-dimensions)
        min-width (/ width 4)
        resize (fn [evt]
                 (let [x (.-clientX evt)]
                   (if (< x min-width)
                     (swap! state assoc-in [:project-manager :width] min-width)
                     (swap! state assoc-in [:project-manager :width] x))))
        gripper (fn []
                  [:div {:draggable true
                         :style {:position :absolute
                                 :cursor :ew-resize
                                 :top 0
                                 :right 0
                                 :width 5
                                 :height "100%"}
                         :on-drag resize
                         :on-drag-end resize}])
        open-file (fn [{:keys [name dir-path] :as file}]
                    (let [cm (js/document.querySelector ".CodeMirror")
                          cm (.. cm -CodeMirror)
                          dir-path (str/join "/" dir-path)
                          file-name (str "/" dir-path "/" name)]
                      (a/go
                        (let [[err file-content] (a/<! (await (js/window.pfs.readFile file-name)))
                              file-content (util/array-buffer->str file-content)]
                          (.. cm getDoc (setValue file-content))))))
        attach-long-press (let [listener-added? (atom false)
                                cm-handler #(let [x (.-clientX %)
                                                  y (.-clientY %)]
                                              ;;(eve/preventDefault %)
                                              (prn "cm-handler")
                                              (show-context-menu x y))]
                            (fn [this-component]
                              (when-let [el (some-> this-component
                                                    dom/dom-node 
                                                    eve/with-long-press)]
                                (when-not @listener-added?
                                  (js/console.log "el=" (hash el) el)
                                  (.. el (addEventListener "contextmenu" cm-handler))
                                  (.. el (addEventListener "longpress" cm-handler))
                                  (reset! listener-added? true)))))]
    (r/create-class {;;:component-did-mount attach-long-press
                     :component-did-update attach-long-press
                     :component-did-mount (fn [this-component]
                                            (prn "did mount"))
                     :component-will-unmount (fn [this-component]
                                               (prn "will unmount"))
                     :reagent-render (fn [state]
                                       (let [current-project (:current-project @state)
                                             project (r/cursor state [:projects current-project])
                                             context-menu-state (r/cursor state [:project-manager :context-menu])
                                             dialog-state (r/cursor state [:dialog])
                                             src-tree (r/cursor project [:src-tree])
                                             ;;visible? (-> @state :project-manager :visible?)
                                             width (-> @state :project-manager :width)]
                                         [:div {:style {:position :absolute
                                                        :left 20
                                                        :top 0
                                                        :z-index 20
                                                        :background-color :white
                                                        :height "100%"
                                                        :width width
                                                        :overflow-x :hidden
                                                        :overflow-y :hidden}
                                                :on-double-click #(hide-context-menu)}
                                          [context-menu context-menu-state]
                                          [dialog dialog-state]
                                          [git-input state]
                                          [dir/tree {:node src-tree :on-click open-file}]
                                          [gripper]]))})))

(defn left-panel [state]
  (let [{:keys [width height]} (util/get-dimensions)
        half-height (- (/ height 2) 10)] 
    [:div {:style {:position :absolute
                   :left 0
                   :top 0}}
     [:div {:style {:transform (util/format "translate(-49%, %dpx) rotate(-90deg)" half-height)
                    :display :grid
                    :grid-template-columns "auto auto" 
                    :width height
                    :height 20}}
      [:button  "Structure"]
      [:button {:on-click toggle-project-manager-visibility} "Project"]]]))

(defn cdr-ui [state]
  [:div
   [left-panel state]
   (when (-> @state :project-manager :visible?)
     [project-manager state])
   [code-area state]])

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


(comment
  (log/merge-config! {:timestamp-opts 
                      {:pattern "yyyy/MM/dd HH:mm:ss ZZ" 
                       }})
  )
