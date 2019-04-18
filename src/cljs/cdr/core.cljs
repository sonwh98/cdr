(ns cdr.core
  (:require [clojure.core.async :as a :include-macros true]
            [cljs.js :as cljs] 
            [com.kaicode.wocket.client :as ws :refer [process-msg]]
            [taoensso.timbre :as log :include-macros true]
            [reagent.core :as r]
            [cdr.mdc :as mdc]
            ;;[stigmergy.mr-clean :as r]
            ))

(def app-state (r/atom {:code-text ""
                        :repl-text ""}))

(def cljs-state (cljs.js/empty-state))
(def async-eval (let [c (a/chan)]
                  (fn [s-expression]
                    (cljs.js/eval cljs-state s-expression {:eval cljs.js/js-eval
                                                           :def-emits-var true
                                                           :verbose true}
                                  (fn [a-map]
                                    (prn (keys a-map))
                                    (if-let [value (:value a-map)]
                                      (do (log/debug a-map)
                                          (a/put! c value))
                                      (let [error {:error a-map}]
                                        (log/debug error)
                                        )
                                      )))
                    c)))
(comment
  (a/go (let [expr '(ns foo.bar)
              r (async-eval expr)]
          (prn "r=" (a/<! r))))
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
  (prn "init")
  )

(defmethod process-msg :chat-broadcast [[_ msg]]
  (prn "from clj " msg)
  (swap! app-state assoc :repl-text msg)
  )

(ws/connect-to-websocket-server {:port 3000})
(r/render-component [cdr-ui app-state] (js/document.getElementById "app"))
