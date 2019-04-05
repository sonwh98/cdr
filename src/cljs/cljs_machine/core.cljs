(ns cljs-machine.core
  (:require [reagent.core :as r]
            [cljs.js :as cljs]
            [clojure.core.async :as a :include-macros true]
            [com.kaicode.wocket.client :as ws :refer [process-msg]]
            [taoensso.timbre :as log :include-macros true]))

(def app-state (r/atom {:client-msg "my chat window"
                        :server-msg "other's chat window"}))


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

(defn chat-area [state]
  (let [client-msg (r/cursor state [:client-msg])
        server-msg (r/cursor state [:server-msg])]
    [:div 
     [:textarea {:style {:width "100%"
                         :height 200}
                 :value @client-msg
                 :on-change #(reset! client-msg (-> % .-target .-value))}]
     [:button {:on-click #(a/go (let [s-expression (cljs.reader/read-string @client-msg)
                                      r (a/<! (async-eval s-expression))]
                                  (prn s-expression)
                                  (prn "r=" r)

                                  )

                                )}
      "eval Send Msg"]
     [:button {:on-click #(reset! client-msg "")}
      "Clear Msg"]

     [:textarea {:style {:width "100%"
                         :height 200}
                 :value @server-msg
                 :read-only true
                 }]
     
     ]
    ))


(defn init []
  (prn "init")
  )

(defmethod process-msg :chat-broadcast [[_ msg]]
  (prn "from clj " msg)
  (swap! app-state assoc :server-msg msg)
  )

(ws/connect-to-websocket-server {:port 3000})
(r/render-component [chat-area app-state] (js/document.getElementById "app"))
