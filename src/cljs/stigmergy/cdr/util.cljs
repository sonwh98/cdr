(ns stigmergy.cdr.util)

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
