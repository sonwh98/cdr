(ns cdr.git
  (:refer-clojure :exclude [clone])
  (:require [clojure.core.async :as a :include-macros true]
            [cljs-await.core :refer [await]]))

(defn clone [{:keys [url dir]}]
  (a/go
    (a/<! (await (js/window.pfs.mkdir dir)))
    (a/<! (await (js/git.clone #js{:dir dir
                                   :corsProxy "https://cors.isomorphic-git.org"
                                   :url url
                                   :ref "master"
                                   :singleBranch true
                                   :depth 10})))))

(comment
  (clone {:url "https://github.com/sonwh98/cdr.git"
          :dir "/cdr" })
  )
