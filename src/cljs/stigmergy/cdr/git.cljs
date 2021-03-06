(ns stigmergy.cdr.git
  (:refer-clojure :exclude [clone])
  (:require [clojure.core.async :as a :include-macros true]
            [taoensso.timbre :as log :include-macros true]
            [cljs-await.core :refer [await]]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            
            [stigmergy.tily.js :as util]
            [stigmergy.cdr.fs :as fs]))

(defn clone [{:keys [url dir]}]
  (a/go
    (a/<! (await (js/window.pfs.mkdir dir)))
    (a/<! (await (js/git.clone (clj->js {:dir dir
                                         :corsProxy "https://cors.isomorphic-git.org"
                                         :url url
                                         :ref "master"
                                         :singleBranch true
                                         :depth 10}))))))

(defn checkout [{:keys [url dir branch]}]
  (a/go
    (a/<! (await (js/git.checkout #js{:dir dir
                                      :url url
                                      :corsProxy "https://cors.isomorphic-git.org"
                                      :ref branch})))))

(defn rm [file]
  (a/go
    (let [[_ dir & other] (str/split file "/")
          dir (str "/" dir)
          filepath (str/join "/" other)
          [err result] (a/<! (await (js/git.remove (clj->js {:dir dir
                                                             :filepath filepath}))))]
      (if err
        (do
          (log/error err)
          err)
        (do
          (log/info "git rm " filepath
                    " result= " result)
          result)))))

(defn listFiles [params]
  (a/go
    (let [ [err files] (a/<! (await (js/git.listFiles (clj->js params))))]
      (seq files))))

(defn status [params]
  (a/go
    
    (let [[err status-code] (a/<! (await (js/git.status (clj->js params))))]
      (if err
        err
        status-code))))

(defn status-matrix [params]
  (a/go
    (let [[err status] (a/<! (await (js/git.statusMatrix (clj->js params))))]
      (if err
        err
        (mapv vec status)))))

(defn commit [dir msg]
  (a/go
    (let [sha (a/<! (await (js/git.commit (clj->js {:dir dir
                                                    :message msg
                                                    :author #js{:name "Sun Tzu"
                                                                :email "son.c.to@gmail.com"}}))))]
      sha)))

(defn log [dir]
  (a/go
    (let [[err result] (a/<! (await (js/git.log (clj->js {:dir dir
                                                          :ref "master"}))))]
      (if err
        err
        (map util/obj->clj result)))))

(comment
  (fs/write-file "/cdr/project.clj" (util/str->array-buffer "hello world3"))

  (a/go
    (pp/pprint (a/<! (log "/tweenie"))))
  
  (a/go
    (prn (a/<! (commit "/tweenie" "deleted project.clj and README.md"))))
  
  (a/go
    (prn (a/<! (status {:dir "/tweenie" :filepath "project.clj"})))
    (prn (a/<! (status {:dir "/tweenie" :filepath "README.md"})))
    )

  (a/go
    (prn (a/<! (status-matrix {:dir "/cdr" :pattern "*"}))))
  (a/go
    (prn (a/<! (listFiles {:dir "/cdr" :ref "HEAD"}))))

  (a/go
    (prn (a/<! (listFiles {:dir "/cdr"}))))
  
  (a/go (let [files ["/tweenie/project.clj" "/tweenie/src/com/kaicode/tweenie.cljs"]]
          (doseq [file files]
            (prn (a/<! (rm file)))
            (prn (a/<! (fs/rm file)))))
        )
  
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
