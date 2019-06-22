(ns stigmergy.cdr.state
  (:require [reagent.core :as r]))

(def app-state (r/atom {:current-ns 'cljs.user
                        :projects {"cdr" {:git {:url ""
                                                :username ""
                                                :password ""}
                                          :src-tree nil}}
                        :project-manager {:visible? true}
                        :context-menu {:x 0 :y 0
                                       :visible? false}
                        :dialog {:visible? false
                                 :content nil}}))

(comment
  (get-in @app-state [:projects "cdr" :git :username])
  (get-in @app-state [:projects "cdr" :git :password])
  
  )
