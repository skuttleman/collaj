(ns com.ben-allred.collaj.enhancers
    (:require #?@(:clj  [[clojure.core.async :as async]
                         [clojure.core.async.impl.protocols :as async.protocols]]
                  :cljs [[cljs.core.async :as async]
                         [cljs.core.async.impl.protocols :as async.protocols]])))

(defn ^:private chan? [x]
    (and (satisfies? async.protocols/Channel x)
        (satisfies? async.protocols/ReadPort x)))

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

(def with-chan-dispatch
    "Enables dispatching a core.async channel which dispatches values placed on the channel."
    (update-dispatch (fn [next get-state]
                         (fn dispatch [action]
                             (if (chan? action)
                                 (do (async/go-loop []
                                         (when-let [value (async/<! action)]
                                             (next value)
                                             (recur)))
                                     nil)
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

(defn with-watchers
    "Adds a :watch fn to store that takes a function to be notified when a specified path
    into state changes upon an action being dispatched. The function will be called with
    the old state (at the specified path) and the new state (at the specified path). Returns
    a function that will unwatch.
    ex: (let [{watch :watch} (collaj/create-store my-reducer collaj/with-watchers)]
            (watch [:some :path :into :state] println)
            (watch [] do-something-when-anything-in-state-changes)"
    [next]
    (fn [reducer initial-state]
        (next reducer initial-state)
        (let [watchers (atom {})
              watch    (fn [path f]
                           (let [key (gensym)]
                               (swap! watchers assoc key [path f])
                               (fn [] (swap! watchers dissoc key))))
              notify!  (fn [dispatch get-state]
                           (fn [action]
                               (let [old-state (get-state)
                                     result    (dispatch action)
                                     new-state (get-state)]
                                   (doseq [[_ [path f]] @watchers
                                           :let [path      (seq path)
                                                 old-state (if path (get-in old-state path) old-state)
                                                 new-state (if path (get-in new-state path) new-state)]
                                           :when (not= old-state new-state)]
                                       (f old-state new-state))
                                   result)))
              store    (next reducer initial-state)]
            (-> store
                (update :dispatch notify! (:get-state store))
                (assoc :watch watch)))))
