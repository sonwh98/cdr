(ns cdr.mdc
  (:require [reagent.core :as r]
            [reagent.dom :as dom]))

;;material component for web
;;https://github.com/material-components/material-components-web/blob/master/docs/getting-started.md

(def button (r/create-class
             {:component-did-mount (fn [this]
                                     (let [element (dom/dom-node this)]
                                       (js/mdc.ripple.MDCRipple.attachTo element)))
              :reagent-render (fn [& params]
                                (let [ [attr-map body] params]
                                  [:button.mdc-button attr-map body])
                                )}))
