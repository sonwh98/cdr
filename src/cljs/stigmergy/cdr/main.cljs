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
  (swap! state/app-state assoc-in [:context-menu :visible?] false))

(defn show-context-menu [{:keys [x y menu-items] :as params}]
  (swap! state/app-state assoc-in [:context-menu] (merge params {:visible? true})))

(defn menu-item [{:keys [label on-click]}]
  [:a {:href "#"
       :on-click #(do
                    (hide-context-menu)
                    (when on-click
                      (on-click %)))} label])

(defn menu-label [{:keys [label on-click]}]
  [:div {:style {:background-color "#eee"
                 :color :black
                 :padding 0
                 :margin 0}} label
   [:svg {:height 1 :width "100%"}
    [:line {:x1 0 :y1 0
            :x2 "100%" :y2 0
            :style {:stroke :black
                    :stroke-width 2}}]]])

(defn hide-dialog []
  (swap! state/app-state assoc-in [:dialog :visible?] false))

(defn show-dialog [content]
  (swap! state/app-state update-in [:dialog] (fn [dialog-state]
                                               (let [content (if (fn? content)
                                                               [content]
                                                               content)]
                                                 (-> dialog-state
                                                     (assoc :visible? true)
                                                     (assoc :content content))))))

(defn clone-ui []
  (let [value (r/atom "")
        cursor (r/atom :auto)
        username (r/atom "")
        password (r/atom "")
        on-change (fn [evt a-atom]
                    (let [v (-> evt .-target .-value)]
                      (reset! a-atom v)))]
    (fn []
      [:div {:style {:cursor @cursor}}
       [:input {:type :text
                :placeholder "github project URL"
                :style {:width "100%"}
                :value @value
                :on-change #(on-change % value)}]
       [:input {:type :text
                :style {:width "100%"}
                :placeholder "optional github user-name"
                :value @username
                :on-change #(on-change % username)
                }]
       [:br]
       [:input {:type :text
                :placeholder "optional github password"
                :value @password
                :on-change #(on-change % password)
                :style {:width "100%"}}]
       [:br]
       [:div.mdc-button
        {:on-click (fn [evt]
                     (let [git-url @value
                           repo-name (some-> git-url
                                             (str/split "/")
                                             last
                                             (str/replace ".git" ""))
                           dir (str "/" repo-name)
                           files (atom [])                           ]
                       (a/go
                         (reset! cursor :wait)
                         #_(swap! state/app-state assoc :current-project repo-name)
                         (swap! state/app-state assoc-in
                                [:projects repo-name :git :username]
                                @username)
                         (swap! state/app-state assoc-in
                                [:projects repo-name :git :password]
                                @password)
                         (a/<! (git/clone {:url git-url
                                           :dir dir}))

                         (let [src-tree (a/<! (fs/mk-dir-tree dir))]
                           (swap! state/app-state assoc-in
                                  [:projects repo-name :src-tree]
                                  src-tree)
                           (hide-dialog)))))}
        "clone"]])))

(def git-top-menu [[menu-label {:label "Git"}]
                   [menu-item {:label "clone"
                               :on-click #(show-dialog clone-ui)}]
                   [menu-item {:label "checkout"
                               :on-click #(show-dialog clone-ui)}]
                   [menu-item {:label "branch"
                               :on-click #(show-dialog [:h1 "branch"])}]
                   [menu-item {:label "commit"}]
                   [menu-item {:label "pull"}]
                   [menu-item {:label "push"}]
                   [menu-item {:label "reset"}]])

(def git-file-menu [[menu-label {:label "Git"}]
                    [menu-item {:label "commit"}]
                    [menu-item {:label "diff"}]
                    [menu-item {:label "history"}]
                    [menu-item {:label "rename"}]
                    [menu-item {:label "reset"}]
                    [menu-item {:label "rm"}]
                    ])

(defn context-menu [context-menu-state]
  (into [:div {:class "vertical-menu"
               :style {:position :absolute
                       :z-index 100
                       :left (:x @context-menu-state)
                       :top (:y @context-menu-state)}}]
        (:menu-items @context-menu-state)))

(defn dialog [dialog-state]
  [:div {:id "myModal", :class "modal"
         :style {:display :block
                 :z-index 100}}
   [:div {:class "modal-content"}
    [:span {:class "close"
            :on-click hide-dialog} "×"]
    (some-> dialog-state deref :content)
    #_[:p "Some text in the Modal.."]]])

(defn toggle-project-manager-visibility []
  (swap! state/app-state update-in [:project-manager :visible?] not))

(defn close-project-manager []
  (swap! state/app-state assoc-in [:project-manager :visible?] false))

(defn code-area [state]
  (let [codemirror (atom nil)]
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
                               (.. cm (on "focus"#(do
                                                    (close-project-manager)
                                                    (hide-context-menu))))
                               (.. cm (on "contextmenu"
                                          (fn [code-mirror evt]
                                            (let [x (- (.-clientX evt) 15)
                                                  y (.-clientY evt)]
                                              (.. evt preventDefault)
                                              (show-context-menu
                                               {:x x :y y
                                                :menu-items [[menu-item {:label "foobar"
                                                                         :on-click #(show-dialog clone-ui)}]
                                                             [menu-item {:label "checkout"
                                                                         :on-click #(show-dialog clone-ui)}]
                                                             [menu-item {:label "branch"
                                                                         :on-click #(show-dialog [:h1 "branch"])}]
                                                             [menu-item {:label "commit"}]
                                                             [menu-item {:label "push"}]
                                                             [menu-item {:label "reset"}]]})))))
                               (reset! codemirror cm)
                               (js/parinferCodeMirror.init cm)))
      :reagent-render (fn [state]
                        (let [{:keys [width height]} (util/get-dimensions)]
                          [:div {:style {:position :absolute
                                         :left 20
                                         :width "100%"}}
                           [:textarea#editor]
                           [mdc/button {:on-click #(a/go (let [txt (.. @codemirror getValue)
                                                               s-expression (cljs.reader/read-string
                                                                             (str "(do " txt ")"))
                                                               r (a/<! (core/async-eval s-expression))]
                                                           (prn s-expression)
                                                           (prn "r=" r)))}
                            "Eval"]]))})))


(defn get-code-mirror []
  (let [cm (js/document.querySelector ".CodeMirror")]
    cm (.. cm -CodeMirror)))

(def project-manager (let [open-file (fn  [{:keys [name dir-path] :as file}]
                                       (let [cm (get-code-mirror)
                                             dir-path (str/join "/" dir-path)
                                             file-name (str "/" dir-path "/" name)]
                                         (a/go
                                           (let [[err file-content] (a/<! (await (js/window.pfs.readFile file-name)))
                                                 file-content (util/array-buffer->str file-content)]
                                             (.. cm getDoc (setValue file-content))))))
                           show-file-options (fn [x y]
                                               (show-context-menu
                                                {:x x :y y
                                                 :menu-items git-file-menu}))
                           {:keys [width height]} (util/get-dimensions)
                           project-manager-state (r/cursor state/app-state [:project-manager])
                           min-width (/ width 4)
                           resize (fn [evt]
                                    (let [x (.-clientX evt)]
                                      (if (< x min-width)
                                        (swap! project-manager-state assoc-in [:width] min-width)
                                        (swap! project-manager-state assoc-in [:width] x))))
                           gripper (fn [] [:div {:draggable true
                                                 :style {:position :absolute
                                                         :cursor :ew-resize
                                                         :top 0
                                                         :right 0
                                                         :width 5
                                                         :height "100%"}
                                                 :on-drag resize
                                                 :on-drag-end resize}])]
                       (r/create-class
                        {:component-did-mount
                         (fn [this-component]
                           (when-let [el (some-> this-component
                                                 dom/dom-node 
                                                 #_eve/with-long-press)]
                             (a/go (let [git-repositories (a/<! (fs/ls "/"))]
                                     (doseq [repo-name git-repositories]
                                       (let [dir (str "/" repo-name)
                                             src-tree (a/<! (fs/mk-dir-tree dir))]
                                         (swap! state/app-state assoc-in
                                                [:projects repo-name :src-tree]
                                                src-tree)))))
                             
                             (.. el (addEventListener "contextmenu"
                                                      (fn [evt]
                                                        (let [x (- (.-clientX evt) 15)
                                                              y (.-clientY evt)]
                                                          (.. evt preventDefault)
                                                          (show-context-menu
                                                           {:x x :y y
                                                            :menu-items git-top-menu})))))
                             #_(.. el (addEventListener "longpress"
                                                        #(contextmenu-handler (get-code-mirror) %)))))
                         :reagent-render
                         (fn [project-manager-state]
                           (let [width (or (-> @project-manager-state :width)
                                           min-width)
                                 projects-state (r/cursor state/app-state [:projects])]
                             [:div {:style {:position :absolute
                                            :display (if (-> @project-manager-state :visible?)
                                                       :block
                                                       :none)
                                            :left 20
                                            :top 0
                                            :z-index 20
                                            :background-color :white
                                            :height "100%"
                                            :width width
                                            :overflow-x :hidden
                                            :overflow-y :auto}
                                    :on-click #(hide-context-menu)}

                              (for [[project-name {:keys [src-tree]}] @projects-state
                                    :when (-> src-tree nil? not)
                                    :let [st (r/cursor projects-state [project-name :src-tree])]]
                                ^{:key project-name} [dir/tree {:node st :on-click open-file
                                                                :on-context-menu show-file-options}])
                              [gripper]]))})))

(defn left-panel []
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
  (let [context-menu-state (r/cursor state [:context-menu])
        project-manager-state (r/cursor state [:project-manager])
        dialog-state (r/cursor state [:dialog])]

    (fn [state]

      [:div
       [left-panel]
       [project-manager project-manager-state]
       (when (-> @context-menu-state :visible?)
         [context-menu context-menu-state])
       (when (-> @dialog-state :visible?)
         [dialog dialog-state])
       [code-area state]]))) 


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
