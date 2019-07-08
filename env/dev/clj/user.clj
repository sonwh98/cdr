(ns user
  (:require [figwheel-sidecar.repl-api :as f]
            [stigmergy.cdr.server :as s]
            [taoensso.timbre :as log :include-macros true]))

(defn start []
  (s/start)
  (f/start-figwheel!))

(defn stop []
  (f/stop-figwheel!)
  (s/stop))

(defn cljs []
  (f/cljs-repl))

(log/set-level! :info)

