(ns stigmergy.cdr.git
  (:refer-clojure :exclude [clone])
  (:require [clojure.core.async :as a :include-macros true]
            [cljs-await.core :refer [await]]
            [stigmergy.cdr.util :as util]))

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
    (let [[code ab] (a/<! (await (js/window.pfs.readFile "/cdr/foo.txt")))
          file (util/array-buffer->str ab)]
      (prn file)
      )
    )

  (a/go
    (let [r (a/<! (await (js/window.pfs.writeFile "/cdr/foo.txt" (util/str->array-buffer "foobar testing commit"))))]
      (prn "r=" r)
      )
    )
  
  (a/go
    (let [sha (a/<! (await (js/git.commit #js{:dir "/cdr"
                                              :message "add foo.txt"
                                              :author #js{:name "Sun Tzu"
                                                          :email "son.c.to@gmail.com"}})))]
      (js/console.log sha))
    )

  (a/go
    (let [r (a/<! (await (js/git.push #js{:dir "/cdr"
                                          :remote "origin"
                                          :ref "master"
                                          :username "sonwh98"
                                          :password "foobar"})))]
      (prn "r=" r)
      )
    )
  
  (js/console.log #js{:dir "/cdr"
                      :message "testing"
                      :author #js{:name "Sun Tzu"
                                  :email "son.c.to@gmail.com"}})
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
