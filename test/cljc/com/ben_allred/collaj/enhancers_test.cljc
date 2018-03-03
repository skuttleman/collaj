(ns com.ben-allred.collaj.enhancers-test
    (:require #?@(:clj  [[clojure.test :refer [deftest testing is are run-tests]]
                         [clojure.core.async :as async]]
                  :cljs [[cljs.test :refer-macros [deftest testing is are run-tests]]
                         [cljs.core.async :as async]])
                         [com.ben-allred.collaj.core :as collaj]
                         [com.ben-allred.collaj.enhancers :as collaj.en]
                         [support.spies :as spy]
                         [support.core :as spt]))

(defn ^:private sub-spies [spy types]
    (and (spy/called-times? spy (count types))
        (->> types
            (every? (comp (partial spy/called-with? spy) (partial conj []))))))

(defn ^:private create-watcher-store
    ([] (create-watcher-store (constantly {::some ::store})))
    ([reducer]
     (collaj/create-store reducer collaj.en/with-watchers)))

(deftest with-subscribers-test
    (testing "(with-subscribers)"
        (testing "adds :subscribe to store"
            (let [store (collaj/create-store (constantly nil) collaj.en/with-subscribers)]
                (is (fn? (:subscribe store)))))
        (testing "takes subscriptions for any action dispatched"
            (let [{:keys [subscribe dispatch]} (collaj/create-store (constantly nil) collaj.en/with-subscribers)
                  spy (spy/create-spy)]
                (subscribe spy)
                (dispatch [:a])
                (dispatch [:b])
                (dispatch [:c])
                (is (sub-spies spy [:a :b :c]))))
        (testing "takes subscriptions for specified action types"
            (let [{:keys [subscribe dispatch]} (collaj/create-store (constantly nil) collaj.en/with-subscribers)
                  spy (spy/create-spy)]
                (subscribe #{:a :c} spy)
                (dispatch [:a])
                (dispatch [:b])
                (dispatch [:c])
                (is (sub-spies spy [:a :c]))))
        (testing "handles multiple subscriptions"
            (let [{:keys [subscribe dispatch]} (collaj/create-store (constantly nil) collaj.en/with-subscribers)
                  spy-a   (spy/create-spy)
                  spy-ab  (spy/create-spy)
                  spy-bc  (spy/create-spy)
                  spy-all (spy/create-spy)]
                (subscribe #{:a} spy-a)
                (subscribe #{:a :b} spy-ab)
                (subscribe #{:b :c} spy-bc)
                (subscribe spy-all)
                (dispatch [:a])
                (dispatch [:b])
                (dispatch [:c])
                (are [spy types] (sub-spies spy types)
                    spy-a [:a]
                    spy-ab [:a :b]
                    spy-bc [:b :c]
                    spy-all [:a :b :c])))
        (testing "does not interfere with dispatch"
            (let [{:keys [subscribe dispatch get-state]} (collaj/create-store
                                                             (comp :value second second list)
                                                             collaj.en/with-subscribers)
                  spy (spy/create-spy)]
                (subscribe spy)
                (dispatch [:a {:value 1}])
                (dispatch [:b {:value 2}])
                (dispatch [:c {:value 3}])
                (is (= 3 (get-state)))))
        (testing "returns a function that unsubscribes"
            (let [{:keys [subscribe dispatch get-state]} (collaj/create-store
                                                             (comp :value second second list)
                                                             collaj.en/with-subscribers)
                  spy         (spy/create-spy)
                  unsubscribe (subscribe spy)]
                (is (fn? unsubscribe))
                (unsubscribe)
                (dispatch [:a {:value 1}])
                (dispatch [:b {:value 2}])
                (dispatch [:c {:value 3}])
                (is (spy/never-called? spy))))))

(deftest with-channels-test
    (testing "(with-channels)"
        (testing "allows dispatching of channels"
            (let [reducer-spy  (spy/create-spy)
                  dispatch-spy (spy/create-spy)
                  spy-mw       (spt/spy-middleware dispatch-spy)
                  chan         (async/chan 64)
                  {:keys [dispatch]} (collaj/create-store reducer-spy collaj.en/with-chan-dispatch spy-mw)]
                (dispatch chan)))

        (testing "dispatches values placed on the channel"
            (let [reducer-spy     (spy/create-spy)
                  dispatch-spy    (spy/create-spy)
                  spy-mw          (spt/spy-middleware dispatch-spy)
                  chan            (async/chan 64)
                  {:keys [dispatch]} (collaj/create-store reducer-spy collaj.en/with-chan-dispatch spy-mw)
                  test-dispatched #(is (spy/called-with? dispatch-spy [:some :value]))]
                (dispatch chan)
                (is (not (spy/called? dispatch-spy)))
                (async/go
                    (async/>! chan [:some :value])
                    #?(:clj  (do
                                 (Thread/sleep 1)
                                 (test-dispatched))
                       :cljs (.setTimeout js/window test-dispatched 1)))))
        (testing "stops reading channel when channel closes"
            (let [reducer-spy      (spy/create-spy)
                  dispatch-spy     (spy/create-spy)
                  spy-mw           (spt/spy-middleware dispatch-spy)
                  chan             (async/chan 64)
                  {:keys [dispatch]} (collaj/create-store reducer-spy collaj.en/with-chan-dispatch spy-mw)
                  test-no-dispatch #(is (not (spy/called? dispatch-spy)))]
                (dispatch chan)
                (async/close! chan)
                (async/go
                    (async/>! chan [:some :value])
                    #?(:clj  (do
                                 (Thread/sleep 1)
                                 (test-no-dispatch))
                       :cljs (.setTimeout js/window test-no-dispatch 1)))))))

(deftest with-keyword-dispatch-test
    (testing "(with-keyword-dispatch)"
        (testing "allows dispatching keywords"
            (let [reducer-spy  (spy/create-spy)
                  dispatch-spy (spy/create-spy)
                  spy-mw       (spt/spy-middleware dispatch-spy)
                  {:keys [dispatch]} (collaj/create-store reducer-spy collaj.en/with-keyword-dispatch spy-mw)]
                (dispatch :some-type)
                (is (spy/called-with? dispatch-spy [:some-type]))
                (is (spy/called-with? reducer-spy nil [:some-type]))))
        (testing "does not effect dispatching normally"
            (let [reducer-spy  (spy/create-spy)
                  dispatch-spy (spy/create-spy)
                  spy-mw       (spt/spy-middleware dispatch-spy)
                  {:keys [dispatch]} (collaj/create-store reducer-spy collaj.en/with-keyword-dispatch spy-mw)]
                (dispatch [:some-type])
                (is (spy/called-with? dispatch-spy [:some-type]))
                (is (spy/called-with? reducer-spy nil [:some-type]))))))

(deftest with-fn-dispatch-test
    (testing "(with-fn-dispatch)"
        (let [reducer-spy  (spy/create-spy)
              action-spy   (spy/create-spy)
              dispatch-spy (spy/create-spy)
              spy-mw       (spt/spy-middleware dispatch-spy)]
            (testing "allows dispatching fns"
                (let [{:keys [dispatch get-state]} (collaj/create-store reducer-spy :initial-state collaj.en/with-fn-dispatch spy-mw)]
                    (dispatch action-spy)
                    (is (spy/never-called? reducer-spy))
                    (let [[dispatch' get-state'] (ffirst (spy/get-calls action-spy))]
                        (is (= get-state get-state'))
                        (dispatch' [:something])
                        (is (spy/called-with? reducer-spy :initial-state [:something])))))
            (testing "does not effect dispatching normally"
                (let [{:keys [dispatch get-state]} (collaj/create-store reducer-spy :initial-state collaj.en/with-fn-dispatch spy-mw)]
                    (dispatch [:some-type])
                    (is (spy/called-with? dispatch-spy [:some-type]))
                    (is (spy/called-with? reducer-spy :initial-state [:some-type])))))))

(deftest with-log-middleware-test
    (testing "(with-log-middleware)"
        (testing "creates a middleware with a log-fn that is called with an action being dispatched"
            (let [log-spy (spy/create-spy)
                  log-mw  (collaj.en/with-log-middleware log-spy)
                  {:keys [dispatch get-state]} (collaj/create-store (constantly 13) 0 log-mw)]
                (dispatch [:some-type])
                (is (spy/called-times? log-spy 2))
                (is (spy/called-with? log-spy [:some-type]))
                (is (spy/called-with? log-spy 13))))
        (testing "can take two different log-fns"
            (let [log-action-spy (spy/create-spy)
                  log-after-spy  (spy/create-spy)
                  log-mw         (collaj.en/with-log-middleware log-action-spy log-after-spy)
                  {:keys [dispatch get-state]} (collaj/create-store (constantly 13) 0 log-mw)]
                (dispatch [:some-type])
                (is (spy/called-times? log-action-spy 1))
                (is (spy/called-with? log-action-spy [:some-type]))
                (is (spy/called-times? log-after-spy 1))
                (is (spy/called-with? log-after-spy 13))))))

(deftest with-watchers-test
    (testing "(with-watchers)"
        (testing "adds :watch fn to store"
            (let [store (create-watcher-store)]
                (is (fn? (:watch store))))
            (testing "when using :watch"
                (testing "is notified of root changes when path is empty"
                    (let [reducer (fn ([] {::some ::store})
                                      ([_ _] {::some ::update}))
                          {:keys [watch dispatch]} (create-watcher-store reducer)
                          watcher (spy/create-spy)]
                        (watch [] watcher)
                        (dispatch [::something])
                        (is (spy/called-times? watcher 1))
                        (is (spy/called-with? watcher {::some ::store} {::some ::update}))))
                (testing "is notified of changes to state at a specified path"
                    (let [reducer (fn ([] {::some ::store})
                                      ([_ _] {::some ::update}))
                          {:keys [watch dispatch]} (create-watcher-store reducer)
                          watcher (spy/create-spy)]
                        (watch [::some] watcher)
                        (dispatch [::something])
                        (is (spy/called-times? watcher 1))
                        (is (spy/called-with? watcher ::store ::update))))
                (testing "is not notified of other changes to state"
                    (let [reducer (fn ([] {::nested {::always 17 ::changer 0}})
                                      ([state _] (update-in state [::nested ::changer] inc)))
                          {:keys [watch dispatch]} (create-watcher-store reducer)
                          watcher (spy/create-spy)]
                        (watch [::nested ::always] watcher)
                        (dispatch [::something])
                        (is (spy/called-times? watcher 0))))
                (testing "can have multiple watchers"
                    (let [reducer       (fn ([] {::nested {::always 17 ::changer 0}})
                                            ([state _] (update-in state [::nested ::changer] inc)))
                          {:keys [watch dispatch]} (create-watcher-store reducer)
                          watch-always  (spy/create-spy)
                          watch-changer (spy/create-spy)]
                        (watch [::nested ::always] watch-always)
                        (watch [::nested ::changer] watch-changer)
                        (dispatch [::something])
                        (dispatch [::something])
                        (is (spy/called-times? watch-always 0))
                        (is (spy/called-times? watch-changer 2))
                        (is (spy/called-with? watch-changer 0 1))
                        (is (spy/called-with? watch-changer 1 2))))
                (testing "returns an unwatch fn"
                    (let [reducer       (fn ([] {::nested {::always 17 ::changer 0}})
                                            ([state _] (update-in state [::nested ::changer] inc)))
                          {:keys [watch dispatch]} (create-watcher-store reducer)
                          watch-always  (spy/create-spy)
                          watch-changer (spy/create-spy)
                          unwatch       (watch [::nested ::changer] watch-changer)]
                        (watch [::nested ::always] watch-always)
                        (dispatch [::something])
                        (unwatch)
                        (dispatch [::something])
                        (is (spy/called-times? watch-always 0))
                        (is (spy/called-times? watch-changer 1))
                        (is (spy/called-with? watch-changer 0 1))))))))
