(ns stigmergy.cdr.state
  (:require [reagent.core :as r]))

(def app-state (r/atom {:code-text ""
                        :repl-text ""
                        :current-ns 'cljs.user
                        :current-project "cdr"
                        :long-press-start? false
                        :projects {"cdr" {:git {:url ""
                                                :username ""
                                                :password ""}
                                          :src-tree nil}}
                        :project-manager {:visible? true
                                          :context-menu {:x 0 :y 0
                                                         :visible? false}}
                        :dialog {:visible? false
                                 :content nil}
                        }))
