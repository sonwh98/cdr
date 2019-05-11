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
  
  (a/go
    (let [dir "/cdr2"]
      (prn "1->" (a/<! (await (js/window.pfs.mkdir dir))))
      (prn "2->" (a/<! (await (js/window.pfs.readdir dir))))
      (prn "3->" (a/<! (await (js/git.clone #js{:dir "/cdr2"
                                                :corsProxy "https://cors.isomorphic-git.org"
                                                :url "https://github.com/sonwh98/cdr.git"
                                                :ref "master"
                                                :singleBranch true
                                                :depth 10}))))
      (prn "4->" (a/<! (await (js/window.pfs.readdir dir))))
      (prn "4->" (a/<! (await (js/window.pfs.readFile (str dir "/src/clj/user.clj")))))
      ))

  (a/go
    (prn (a/<! (await (js/git.log #js{:dir "/cdr2"}))))
    (let [[err buff] (a/<! (await (js/window.pfs.readFile "/cdr2/src/clj/user.clj")))]
      (prn "user.clj=" (array-buffer->str  buff ) ))
    )

  (a/go
    (let [[err files] (a/<! (await (js/git.listFiles #js{:dir "/cdr2/"
                                                         :ref "HEAD"})))]
      (prn err)
      (prn files)
      ))

  )
