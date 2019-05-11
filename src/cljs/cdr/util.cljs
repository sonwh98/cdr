(ns cdr.util)

(defn array-buffer->str [array-buffer]
  (let [decoder (js/TextDecoder. "UTF-8")]
    (.. decoder (decode array-buffer))))

(defn obj->clj
  [obj]
  (if (goog.isObject obj)
    (-> (fn [result key]
          (let [v (goog.object/get obj key)]
            (if (= "function" (goog/typeOf v))
              result
              (assoc result key (obj->clj v)))))
        (reduce {} (.getKeys goog/object obj)))
    obj))
