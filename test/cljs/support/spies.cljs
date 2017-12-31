(ns support.spies)

(def ^:private spies (atom []))

(defn spy-on [f]
    (let [calls (atom [])
          spy (with-meta
                  (fn [& args]
                      (swap! calls conj args)
                      (apply f args))
                  {:calls calls})]
        (swap! spies conj spy)
        spy))

(defn create-spy []
    (spy-on (constantly nil)))

(defn get-calls [spy]
    (when-let [calls (:calls (meta spy))]
        @calls))

(defn called-with? [spy & args]
    (some (partial = args) (get-calls spy)))

(defn called-times? [spy n]
    (= n (count (get-calls spy))))

(defn called-with-times? [spy n & args]
    (->> spy
        (get-calls)
        (filter (partial = args))
        (count)
        (= n)))

(defn never-called? [spy]
    (empty? (get-calls spy)))

(def called? (complement never-called?))

(defn reset-spy! [spy]
    (when-let [calls (:calls (meta spy))]
        (reset! calls [])))

(defn reset-spies! []
    (doseq [spy @spies
            :when spy]
        (reset-spy! spy)))
