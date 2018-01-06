(ns support.core
    (:require [support.spies :as spy]))

(defn spy-middleware [spy]
    (fn [next]
        (fn [reducer initial-state]
            (-> (next reducer initial-state)
                (update :dispatch comp (fn [action]
                                           (spy action)
                                           action))))))
