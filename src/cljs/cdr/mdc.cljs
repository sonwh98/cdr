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
                                        (js/mdc.tabBar.MDCTabBar. element)))
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

(def drawer (r/create-class
             {:component-did-mount (fn [this]

                                     (let [element (js/document.querySelector ".mdc-drawer")]
                                       (prn "mount " element)
                                       (js/mdc.drawer.MDCDrawer.attachTo element)))
              :reagent-render
              (fn [& params]
                [:div
                 [:aside {:class "mdc-drawer mdc-drawer--dismissible"}
                  [:div {:class "mdc-drawer__content"}
                   [:div {:class "mdc-list"}
                    [:a {:class "mdc-list-item mdc-list-item--activated", :href "#", :aria-current "page"}
                     [:i {:class "material-icons mdc-list-item__graphic", :aria-hidden "true"} "inbox"]
                     [:span {:class "mdc-list-item__text"} "Inbox"]]
                    [:a {:class "mdc-list-item", :href "#"}
                     [:i {:class "material-icons mdc-list-item__graphic", :aria-hidden "true"} "send"]
                     [:span {:class "mdc-list-item__text"} "Outgoing"]]
                    [:a {:class "mdc-list-item", :href "#"}
                     [:i {:class "material-icons mdc-list-item__graphic", :aria-hidden "true"} "drafts"]
                     [:span {:class "mdc-list-item__text"} "Drafts"]]]]]
                 [:div {:class "mdc-drawer-app-content"}
                  [:header {:class "mdc-top-app-bar app-bar", :id "app-bar"}
                   [:div {:class "mdc-top-app-bar__row"}
                    [:section {:class "mdc-top-app-bar__section mdc-top-app-bar__section--align-start"}
                     [:a {:href "#", :class "demo-menu material-icons mdc-top-app-bar__navigation-icon"} "menu"]
                     [:span {:class "mdc-top-app-bar__title"} "Dismissible Drawer"]]]]
                  [:main {:class "main-content", :id "main-content"}
                   [:div {:class "mdc-top-app-bar--fixed-adjust"} "App Content"]]]]
                )}))
