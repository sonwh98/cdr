(ns stigmergy.cdr.main
  (:require [reagent.core :as r]
            [reagent.dom :as dom]
            [clojure.core.async :as a :include-macros true]
            [cljs-await.core :refer [await]]
            [taoensso.timbre :as log :include-macros true]
            [com.kaicode.wocket.client :as ws :refer [process-msg]]
            
            [clojure.string :as str]

            [stigmergy.tily.js :as util]
            [stigmergy.eve :as eve]

            [stigmergy.cdr.state :as state]
            [stigmergy.cdr.core :as core]
            [stigmergy.cdr.mdc :as mdc]
            [stigmergy.cdr.fs :as fs]
            [stigmergy.cdr.git :as git]
            [stigmergy.cdr.dir-navigator :as dir]))

(defn hide-context-menu []
  (swap! state/app-state assoc-in [:project-manager :context-menu :visible?] false))

(defn show-context-menu [x y]
  (swap! state/app-state update-in
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

(defn show-dialog [content]
  (swap! state/app-state update-in [:dialog] (fn [dialog-state]
                                               (let [content (if (fn? content)
                                                               [content]
                                                               content)]
                                                 (-> dialog-state
                                                     (assoc :visible? true)
                                                     (assoc :content content)))))
  )

(defn hide-dialog []
  (swap! state/app-state assoc-in [:dialog :visible?] false))

(defn context-menu [context-menu-state]
  (let [checkout-ui (fn []
                      (let [value (r/atom "https://github.com/sonwh98/cdr.git")
                            current-project (:current-project @state/app-state)
                            cursor (r/atom :auto)]
                        (fn []
                          [:div {:style {:cursor @cursor}}
                           [:input {:type :text
                                    :placeholder "git URL"
                                    :style {:width "100%"}
                                    :value @value
                                    :on-change (fn [evt]
                                                 (reset! value (-> evt .-target .-value)))}]

                           [:div.mdc-button
                            {:on-click (fn [evt]
                                         (let [git-url @value
                                               repo-name (some-> git-url
                                                                 (str/split "/")
                                                                 last
                                                                 (str/replace ".git" ""))
                                               dir (str "/" repo-name)
                                               files (atom [])]
                                           (a/go
                                             (reset! cursor :wait)
                                             (a/<! (git/clone {:url git-url
                                                               :dir dir}))
                                             (a/<! (fs/walk-dir {:dir dir
                                                                 :on-file (fn [file]
                                                                            (when-not (re-find #".git" file)
                                                                              (swap! files conj file)))}))
                                             (let [project-root (fs/mk-project-tree @files)]
                                               (swap! state/app-state assoc-in
                                                      [:projects current-project :src-tree]
                                                      project-root)
                                               (hide-dialog)))))}
                            "GET"]])))]
    [:div {:class "vertical-menu"
           :style {:position :absolute
                   :left (:x @context-menu-state)
                   :top (:y @context-menu-state)}}
     [menu-item {:label "checkout"
                 :on-click #(show-dialog checkout-ui)}]
     [menu-item {:label "branch"
                 :on-click #(show-dialog [:h1 "branch"])}]
     [menu-item {:label "commit"}]
     [menu-item {:label "push"}]
     [menu-item {:label "reset"}]])
  )

(defn dialog [dialog-state]
  (when (:visible? @dialog-state)
    [:div {:id "myModal", :class "modal"
           :style {:display :block}}  
     [:div {:class "modal-content"}
      [:span {:class "close"
              :on-click hide-dialog} "×"]
      (some-> dialog-state deref :content)
      #_[:p "Some text in the Modal.."]]]))

(defn toggle-project-manager-visibility []
  (swap! state/app-state update-in [:project-manager :visible?] not))

(defn close-project-manager []
  (swap! state/app-state assoc-in [:project-manager :visible?] false))

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
                                                               r (a/<! (core/async-eval s-expression))]
                                                           (prn s-expression)
                                                           (prn "r=" r)))}
                            "Eval"]]))})))

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
        attach-long-press (let [contextmenu-handler #(let [x (- (.-clientX %) 15)
                                                           y (.-clientY %)]
                                                       (eve/preventDefault %)
                                                       (show-context-menu x y))]
                            (fn [this-component]
                              (when-let [el (some-> this-component
                                                    dom/dom-node 
                                                    eve/with-long-press)]
                                (.. el (addEventListener "contextmenu" contextmenu-handler))
                                (.. el (addEventListener "longpress" contextmenu-handler)))))]
    (r/create-class {:component-did-mount attach-long-press
                     :reagent-render (fn [state]
                                       (let [current-project (:current-project @state)
                                             project (r/cursor state [:projects current-project])
                                             context-menu-state (r/cursor state [:project-manager :context-menu])
                                             dialog-state (r/cursor state [:dialog])
                                             src-tree (r/cursor project [:src-tree])
                                             width (or (-> @state :project-manager :width)
                                                       min-width)]
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
                                          (when (:visible? @context-menu-state)
                                            [context-menu context-menu-state])
                                          [dialog dialog-state]
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

(defn main-ui [state]
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
  (r/render-component [main-ui state/app-state] (js/document.getElementById "app"))
  (log/set-level! :info))

(defmethod process-msg :chat-broadcast [[_ msg]]
  (prn "from clj " msg)
  (swap! state/app-state assoc :repl-text msg))
