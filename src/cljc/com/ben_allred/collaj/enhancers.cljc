(ns com.ben-allred.collaj.enhancers)

(defn ^:private update-dispatch [f]
    (fn [next]
        (fn [reducer initial-state]
            (let [{:keys [get-state] :as store} (next reducer initial-state)]
                (update store :dispatch f get-state)))))

(defn with-subscribers
    "Adds a :subscribe fn to store that takes a function to be notified
    when an action is dispatched. Returns a function that will unsubscribe.
    Takes optional first argument that is a set of keywords indicating
    which type or types to be notified of on dispatch.
    ex: (let [{subscribe :subscribe} (collaj/create-store my-reducer collaj/with-subscribers)]
            (subscribe #{:some-type :some-other-type} println)"
    [next]
    (fn [reducer initial-state]
        (let [subs      (atom {})
              subscribe (fn subscribe
                            ([f] (subscribe (constantly true) f))
                            ([type-fn f]
                             (let [key (gensym)]
                                 (swap! subs assoc key [type-fn f])
                                 (fn [] (swap! subs dissoc key)))))
              notify!   (fn [[type :as action]]
                            (doseq [[type-fn sub] (vals @subs)
                                    :when (type-fn type)]
                                (sub action))
                            action)]
            (-> (next reducer initial-state)
                (update :dispatch comp notify!)
                (assoc :subscribe subscribe)))))

(def with-keyword-dispatch
    "Enables dispatching a keyword.
    (dispatch :something) is equivalent to (dispatch [:something])."
    (update-dispatch (fn [next _]
                         (fn [action]
                             (if (keyword? action)
                                 (next [action])
                                 (next action))))))

(def with-fn-dispatch
    "Enables dispatching a function which gets called with [dispatch get-state] in a vector."
    (update-dispatch (fn [next get-state]
                         (fn dispatch [action]
                             (if (fn? action)
                                 (action [dispatch get-state])
                                 (next action))))))

(defn with-log-middleware
    "Creates a logging middleware for printing output when actions are dispatched.
    Logs the action being dispatched and the state after the action has been dispatched."
    ([log-fn] (with-log-middleware log-fn log-fn))
    ([log-before log-after]
     (update-dispatch (fn [next get-state]
                          (fn [action]
                              (log-before action)
                              (let [result (next action)]
                                  (log-after (get-state))
                                  result))))))
