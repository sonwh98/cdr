(ns stigmergy.cdr.main
  (:require [stigmergy.cdr.ui :as ui]
            [stigmergy.cdr.state :as state]
            [com.kaicode.wocket.client :as ws :refer [process-msg]]
            [reagent.core :as r]
            [taoensso.timbre :as log :include-macros true]
            ))

(defn init []
  (set! (.-fs js/window) (js/LightningFS. "fs"))
  (js/git.plugins.set "fs" (.-fs js/window))
  (set! (.-pfs js/window)  js/window.fs.promises)
  
  ;;(ws/connect-to-websocket-server {:port 80})
  (r/render-component [ui/cdr-ui state/app-state] (js/document.getElementById "app"))
  (log/set-level! :info))

(defmethod process-msg :chat-broadcast [[_ msg]]
  (prn "from clj " msg)
  (swap! state/app-state assoc :repl-text msg))
