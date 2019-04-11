(ns user
  (:require [figwheel-sidecar.repl-api :as f]
            [cdr.server :as s]))

(defn start []
  (s/start)
  (f/start-figwheel!))

(defn stop []
  (f/stop-figwheel!)
  (s/stop))

(defn cljs []
  (f/cljs-repl))
