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
            ))

(def app-state (r/atom {:code-text ""
                        :repl-text ""
                        :current-ns 'cljs.user}))

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
                                                                             ;;:theme "dracula"
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

(defn cdr-ui [state]
  [:div
   [mdc/drawer {:content [code-area state]
                :drawer-content [:h1 "drawer"]}]
   #_[mdc/tab-bar]
   ])

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
      #_(prn "1->" (a/<! (await (js/window.pfs.mkdir dir))))
      (prn "2->" (a/<! (await (js/window.pfs.readdir dir))))
      #_(js/console.log (a/<! (await (js/git.clone #js{
                                                       :corsProxy "https://cors.isomorphic-git.org"
                                                       :url "https://github.com/sonwh98/cdr.git"
                                                       :ref "master"
                                                       :singleBranch true
                                                       :depth 10}))))
      #_(prn "4->" (a/<! (await (js/window.pfs.readdir dir))))
      (prn "4->" (a/<! (await (js/window.pfs.readFile (str dir "/src/clj/user.clj")))))
      ))

  (a/go
    (prn (a/<! (await (js/git.log #js{:dir "/cdr"})))))
  )
