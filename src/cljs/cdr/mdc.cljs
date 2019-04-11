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
                                  [:button.mdc-button attr-map body]))}))

(def tab-bar (r/create-class
              {:component-did-mount (fn [this]
                                      (let [element (dom/dom-node this)]
                                        (js/mdc.tabBar.MDCTabBar. element))
                                      )
               :reagent-render
               (fn [& params]
                 [:div {:class "mdc-tab-bar", :role "tablist"}
                  [:div {:class "mdc-tab-scroller"}
                   [:div {:class "mdc-tab-scroller__scroll-area"}
                    [:div {:class "mdc-tab-scroller__scroll-content"}
                     [:button {:class "mdc-tab mdc-tab--active", :role "tab", :aria-selected "true", :tabIndex "0"}
                      [:span {:class "mdc-tab__content"}
                       [:span {:class "mdc-tab__icon material-icons", :aria-hidden "true"} "favorite"]
                       [:span {:class "mdc-tab__text-label"} "Favorites"]]
                      [:span {:class "mdc-tab__content"}
                       [:span {:class "mdc-tab__icon material-icons", :aria-hidden "true"} "favorite"]
                       [:span {:class "mdc-tab__text-label"} "Favorites"]]
                      [:span {:class "mdc-tab-indicator mdc-tab-indicator--active"}
                       [:span {:class "mdc-tab-indicator__content mdc-tab-indicator__content--underline"}]]
                      [:span {:class "mdc-tab__ripple"}]]]]]]
                 )}))
