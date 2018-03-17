(ns com.ben-allred.collaj.reducers
    (:refer-clojure :exclude [assoc assoc-in comp identity]))

(def ^:private map-initial
    (map (fn [[key reducer]] [key (reducer)])))

(defn ^:private map-reduction [state action]
    (map (fn [[key reducer]] [key (reducer (get state key) action)])))

(defn ^:private dissoc-in [m & paths]
    (loop [m m [[k :as path] & paths :as ks] paths]
        (cond
            (empty? ks) m
            (= 1 (count path)) (recur (dissoc m k) paths)
            :else  (recur (update-in m (butlast path) dissoc (last path)) paths))))

(defn ^:private identity
    ([] nil)
    ([state action] state))

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
    produces state by assoc'ing the values of additional reducers onto the value
    of the initial reducer"
    [reducer & {:as reducer-map}]
    (let [keys (keys reducer-map)]
        (fn
            ([] ((fnil into {}) (reducer) map-initial reducer-map))
            ([state action] ((fnil into {}) (reducer (apply dissoc state keys) action)
                                (map-reduction state action)
                                reducer-map)))))

(defn assoc-in
    "Given a reducer and any number of key paths -> reducers, returns a reducer which
    produces state by assoc'ing in the values of additional reducers onto the value
    of the initial reducer"
    [reducer & {:as reducer-map}]
    (let [keys (keys reducer-map)]
        (fn
            ([]
             (->> reducer-map
                 (map (fn [[ks reducer]] [ks (reducer)]))
                 (reduce (partial apply clojure.core/assoc-in) (reducer))))
            ([state action]
             (->> reducer-map
                 (map (fn [[ks reducer]] [ks (reducer (get-in state ks) action)]))
                 (reduce (partial apply clojure.core/assoc-in) (reducer (apply dissoc-in state keys) action)))))))

(defn map-of
    "Given a key-fn and reducer, it returns a reducer which manages the state of an item
    in the map when the key-fn return non-null value."
    [key-fn reducer]
    (let [initial-value (delay (reducer))]
        (fn ([] {})
            ([state action]
             (let [key       (key-fn action)
                   key-some? (some? key)
                   has-key?  (contains? state key)]
                 (cond
                     has-key? (update state key reducer action)
                     key-some? (clojure.core/assoc state key (reducer @initial-value action))
                     :else state))))))

(defn comp
    "Composes reducers (right to left) passing as state the response of the
    right-most reducer to the reducer next to it and so on. Uses the first reducer for initial-state if needed."
    ([] identity)
    ([reducer] reducer)
    ([reducer & reducers]
     (let [composed (->> reducers
                        (reduce (fn [combined reducer] #(combined (reducer %1 %2) %2)) reducer))]
         (fn ([] (reducer))
             ([state action] (composed state action))))))
