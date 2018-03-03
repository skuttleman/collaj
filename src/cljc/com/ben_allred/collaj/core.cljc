(ns com.ben-allred.collaj.core
    (:require #?(:clj [clojure.spec.alpha :as s]
                 :cljs [cljs.spec.alpha :as s])))

(s/def ::dispatchable (s/and sequential? (comp keyword? first)))

(defn apply-middleware
    "Creates an enhancer out of one or more fns that take get-state
    And return a function that takes dispatch and returns a new dispatch fn."
    [& middlewares]
    (fn [next]
        (fn [reducer initial-state]
            (let [{:keys [get-state] :as store} (next reducer initial-state)
                  dispatch (->> middlewares
                               (map #(% get-state))
                               (apply comp)
                               (#(% (:dispatch store))))]
                (assoc store :dispatch dispatch)))))

(defn enhance-reducer
    "Makes an enhancer out of one or more functions (implicitely composed with comp)
    that take in the reducer and return an enhanced reducer."
    [& enhancers]
    (fn [next]
        (fn [reducer initial-state]
            (->> enhancers
                (apply comp)
                (#(% reducer))
                (#(next % initial-state))))))

(defn ^:private dispatch-wrapper [dispatch]
    (apply-middleware (fn [_] (fn [next] (comp second (juxt dispatch next))))))

(defn ^:private build-store [atom-fn]
    (fn create
        ([reducer initial-state enhancers]
         (((apply comp (remove nil? enhancers)) create) reducer initial-state))
        ([reducer initial-state]
         (let [state     (atom-fn initial-state)
               get-state (fn [] @state)
               dispatch  (fn [dispatchable]
                             (if (s/valid? ::dispatchable dispatchable)
                                 (swap! state reducer dispatchable)
                                 (throw (ex-info
                                            (pr-str (s/explain-data ::dispatchable dispatchable))
                                            {:dispatched dispatchable}))))]
             {:dispatch dispatch :get-state get-state}))))

(defn create-custom-store
    ([atom-fn reducer]
     ((build-store atom-fn) reducer (reducer)))
    ([atom-fn reducer initial-state & enhancers]
     (if (fn? initial-state)
         ((build-store atom-fn) reducer (reducer) (conj enhancers initial-state))
         ((build-store atom-fn) reducer initial-state enhancers))))

(defn create-custom-local-store
    ([atom-fn dispatch reducer]
     ((build-store atom-fn) reducer (reducer) [(dispatch-wrapper dispatch)]))
    ([atom-fn dispatch reducer initial-state & enhancers]
     (if (fn? initial-state)
         ((build-store atom-fn) reducer (reducer) (conj enhancers initial-state (dispatch-wrapper dispatch)))
         ((build-store atom-fn) reducer initial-state (conj enhancers (dispatch-wrapper dispatch))))))

(def create-store (partial create-custom-store atom))

(def create-local-store (partial create-custom-local-store atom))
