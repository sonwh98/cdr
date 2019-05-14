(ns cdr.dir-navigator)

(defn toggle [evt]
  (let [element (.-target evt)
        parent (.-parentElement element)]
    (.. parent (querySelector ".sub-dir") -classList (toggle "active"))))

(defn get-name [node]
  (-> node keys first))

(defn get-children [node]
  (-> node vals first))

(defn dir [node on-click]
  [:li
   [:span {:class "dir"
           :on-click toggle} (get-name node)]
   [:ul {:class "sub-dir"
         :style {:list-style-type :none}}
    (for [c (get-children node)]
      (with-meta (if (string? c)
                   [:li {:on-click #(on-click c)} c]
                   [dir c])
        {:key (str c)}))]])

(defn tree [{:keys [node on-click] }]
  (when-not (empty? @node)
    [:ul {:style {:list-style-type :none
                  :margin 0
                  :padding 0}}
     [dir @node on-click]]))
