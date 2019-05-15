(ns cdr.util)

(defn array-buffer->str [array-buffer]
  (let [decoder (js/TextDecoder. "UTF-8")]
    (.. decoder (decode array-buffer))))

(defn str->array-buffer [str]
  (let [e (js/TextEncoder. "UTF-8")]
    (.. e (encode str))))

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

(defn in? [e coll]
  (boolean (some #(= % e) coll)))

(defn cmp [b a]
  (let [b-count (count b)
        a-count (count a)
        c (if (> b-count a-count)
            (map (fn [[i a-element]]
                   (let [b-element (b i)]
                     (compare b-element a-element)))
                 (map-indexed (fn [i item] [i item]) a))
            (map (fn [[i b-element]]
                   (let [a-element (a i)]
                     (compare b-element a-element)))
                 (map-indexed (fn [i item] [i item]) b)))
        c   (some (fn [v] (if (= 0  v)
                            0
                            v
                            )) c)]

    c))
