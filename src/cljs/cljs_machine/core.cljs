(ns cljs-machine.core
  (:require [reagent.core :as r]
            [cljs.js :as cljs]
            [clojure.core.async :as a :include-macros true]
            [com.kaicode.wocket.client :as ws :refer [process-msg]]
            [taoensso.timbre :as log :include-macros true]
            #_[cljsjs.codemirror]))

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
                                                                             })
                                   button (js/document.querySelector ".foo-button")]
                               (reset! codemirror cm)
                               (js/parinferCodeMirror.init cm)
                               (js/mdc.ripple.MDCRipple.attachTo button)
                               ))
      :reagent-render (fn [state]
                        [:div 
                         [:textarea#editor {:style {:width "100%"
                                                    :height 200}
                                            
                                            }]
                         [:button {:on-click #(a/go (let [txt (.. @codemirror getValue)
                                                          s-expression (cljs.reader/read-string txt)
                                                          r (a/<! (async-eval s-expression))]
                                                      (prn s-expression)
                                                      (prn "r=" r)))}
                          "Eval"]
                         [:button {:on-click #(reset! code-text "")}
                          "Clear"]
                         [:button {:class "foo-button mdc-button"} "Button"]
                         ])})))

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
   [code-area state]
   #_[code-area state]])

(defn init []
  (prn "init")
  )

(defmethod process-msg :chat-broadcast [[_ msg]]
  (prn "from clj " msg)
  (swap! app-state assoc :repl-text msg)
  )

(ws/connect-to-websocket-server {:port 3000})
(r/render-component [cdr-ui app-state] (js/document.getElementById "app"))
