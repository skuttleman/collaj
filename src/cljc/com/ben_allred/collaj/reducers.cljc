(ns com.ben-allred.collaj.reducers
    (:refer-clojure :exclude [assoc]))

(def ^:private map-initial
    (map (fn [[key reducer]] [key (reducer)])))

(defn ^:private map-reduction [state action]
    (map (fn [[key reducer]] [key (reducer (get state key) action)])))

(defn combine
    "Given a map of arbitrary keys to reducers, returns a reducing function that
    garentees state tree by calling each reducing function with its part of the state
    tree when an action is dispatched."
    [reducer-map]
    (fn
        ([] (into {} map-initial reducer-map))
        ([state action] (into {} (map-reduction state action) reducer-map))))

(defn assoc
    "Given a reducer and any number of keys -> reducers, returns a reducer which
    Producers state by assoc'ing the values of additional reducers onto the value
    of the initial reducer"
    [reducer & {:as reducer-map}]
    (let [keys (keys reducer-map)]
        (fn
            ([] (into (reducer) map-initial reducer-map))
            ([state action] (into (reducer (apply dissoc state keys) action)
                                (map-reduction state action)
                                reducer-map)))))
