(ns stigmergy.cdr.init
  (:require [stigmergy.cdr.main :as cdr]))

(enable-console-print!)
(prn "prod environment")
(cdr/init)
