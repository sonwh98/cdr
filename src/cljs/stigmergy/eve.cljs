(ns stigmergy.eve)

(def with-long-press (let [timer-id (atom nil)]
                       (fn [element {:keys [on-mouse-up on-mouse-down time-out]
                                     :or {time-out 1000}}]
                         (.. element (addEventListener "mousedown"
                                                       (fn [evt]
                                                         (reset! timer-id
                                                                 (js/setTimeout
                                                                  #(.. element (dispatchEvent
                                                                                (js/MouseEvent. "longpress" evt)))
                                                                  time-out))
                                                         (when on-mouse-down
                                                           (on-mouse-down evt)))))
                         (.. element (addEventListener "mouseup"
                                                       (fn [evt]
                                                         (js/clearTimeout @timer-id)
                                                         (when on-mouse-up
                                                           (on-mouse-up evt)))))
                         element)))

