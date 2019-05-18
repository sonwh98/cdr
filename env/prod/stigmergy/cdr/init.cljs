(ns stigmergy.cdr.init
  (:require [stigmergy.cdr.core :as cdr]))

(enable-console-print!)
(prn "prod environment")
(cdr/init)
