(ns stigmergy.cdr.init
  (:require [stigmergy.cdr.main :as cdr]
            [figwheel.client :as figwheel :include-macros true]))

(enable-console-print!)

(defn setup-figwheel []
  ;;NOTE to get figwheel to work for remote pairing on a server behind a firewall or a NAT,
  ;;an nginx reverse proxy is necessary. see the sample nginx file conf/lab.stigmergy.systems
  (let [protocol window.location.protocol
        port window.location.port
        ws-url (if port
                 (str "ws://[[client-hostname]]:" port "/figwheel-ws")
                 "ws://[[client-hostname]]/figwheel-ws")
        wss-url "wss://[[client-hostname]]/figwheel-ws" ;;need to configure nginx
        websocket-url (if (= "https:" protocol)
                        wss-url
                        ws-url)]
    (figwheel/watch-and-reload
     :websocket-url websocket-url)))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )

(prn "dev environment")
(setup-figwheel)
(cdr/init)
