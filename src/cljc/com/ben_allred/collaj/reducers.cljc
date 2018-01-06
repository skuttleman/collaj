(ns com.ben-allred.collaj.reducers)

(defn combine-reducers
    "Given a map of arbitrary keys to reducers, returns a reducing function that
    garentees state tree by calling each reducing function with its part of the state
    tree when an action is dispatched."
    [reducer-map]
    (fn combiner
        ([] (combiner #(%2)))
        ([state action] (combiner #(%2 (get state %1) action)))
        ([mapper]
         (->> reducer-map
             (map (fn [[key reducer]] [key (mapper key reducer)]))
             (into {})))))
