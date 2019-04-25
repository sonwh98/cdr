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
                                     (let [this-el (dom/dom-node this)
                                           mdc-drawer (.. this-el (querySelector ".mdc-drawer"))
                                           drawer (js/mdc.drawer.MDCDrawer.attachTo mdc-drawer)
                                           drawer-button (.. this-el (querySelector "button"))
                                           main-content-el (.. this-el (querySelector ".drawer-main-content"))]
                                       (.. drawer-button (addEventListener "click"
                                                                           #(set! (.-open drawer) true)))
                                       (js/document.body.addEventListener "MDCDrawer:closed"
                                                                          #(.. main-content-el focus))))
              :reagent-render
              (fn [{:keys [content drawer-content] :as params}]
                [:div {:class "drawer-frame-root"}
                 [:aside {:class "mdc-drawer mdc-drawer--modal"}
                  [:div {:class "mdc-drawer__header"}
                   [:h3 {:class "mdc-drawer__title"} "Project"]
                   [:h6 {:class "mdc-drawer__subtitle"} "CDR"]]
                  [:div {:class "mdc-drawer__content"}
                   [:nav {:class "mdc-list"}
                    drawer-content
                    
                    
                    [:hr {:class "mdc-list-divider"}]
                    [:h6 {:class "mdc-list-group__subheader"} "Labels"]
                    [:a {:class "mdc-list-item", :href "#", :tabIndex "-1"}
                     [:i {:class "material-icons mdc-list-item__graphic", :aria-hidden "true"} "bookmark"] "Family"]
                    [:a {:class "mdc-list-item", :href "#", :tabIndex "-1"}
                     [:i {:class "material-icons mdc-list-item__graphic", :aria-hidden "true"} "bookmark"] "Friends"]
                    [:a {:class "mdc-list-item", :href "#", :tabIndex "-1"}
                     [:i {:class "material-icons mdc-list-item__graphic", :aria-hidden "true"} "bookmark"] "Work"]
                    [:hr {:class "mdc-list-divider"}]
                    [:a {:class "mdc-list-item", :href "#", :tabIndex "-1"}
                     [:i {:class "material-icons mdc-list-item__graphic", :aria-hidden "true"} "settings"] "Settings"]
                    [:a {:class "mdc-list-item", :href "#", :tabIndex "-1"}
                     [:i {:class "material-icons mdc-list-item__graphic", :aria-hidden "true"} "announcement"] "Help &amp; feedback"]]]]
                 [:div {:class "mdc-drawer-scrim"}]
                 [:div {:class "drawer-frame-app-content"}
                  [:header {:class "mdc-top-app-bar drawer-top-app-bar"}
                   [:div {:class "mdc-top-app-bar__row"}
                    [:button {:class "material-icons mdc-top-app-bar__navigation-icon "} "menu"]]]
                  [:div {:class "drawer-main-content"
                         :tabIndex 1}
                   [:div {:class "mdc-top-app-bar--fixed-adjust"}]
                   content
                   ]]]
                )}))
