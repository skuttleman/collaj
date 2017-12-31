(ns com.ben-allred.collaj.enhancers-test
    (:require [cljs.test :refer-macros [deftest testing is are run-tests use-fixtures]]
              [com.ben-allred.collaj.core :as collaj]
              [com.ben-allred.collaj.enhancers :as collaj.en]
              [support.spies :as spy]
              [support.core :as spt]))

(enable-console-print!)

(use-fixtures :each {:before spy/reset-spies!})

(defn ^:private sub-spies [spy types]
    (and (spy/called-times? spy (count types))
        (->> types
            (every? (comp (partial spy/called-with? spy)
                        (partial conj []))))))

(deftest with-subscribers-test
    (testing "with-subscribers"
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
                  spy-a (spy/create-spy)
                  spy-ab (spy/create-spy)
                  spy-bc (spy/create-spy)
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
                  spy (spy/create-spy)
                  unsubscribe (subscribe spy)]
                (is (fn? unsubscribe))
                (unsubscribe)
                (dispatch [:a {:value 1}])
                (dispatch [:b {:value 2}])
                (dispatch [:c {:value 3}])
                (is (spy/never-called? spy))))))

(deftest with-keyword-dispatch-test
    (testing "with-keyword-dispatch"
        (testing "allows dispatching keywords"
            (let [reducer-spy (spy/create-spy)
                  dispatch-spy (spy/create-spy)
                  spy-mw (spt/spy-middleware dispatch-spy)
                  {:keys [dispatch]} (collaj/create-store reducer-spy collaj.en/with-keyword-dispatch spy-mw)]
                (dispatch :some-type)
                (is (spy/called-with? dispatch-spy [:some-type]))
                (is (spy/called-with? reducer-spy nil [:some-type]))))
        (testing "does not effect dispatching normally"
            (let [reducer-spy (spy/create-spy)
                  dispatch-spy (spy/create-spy)
                  spy-mw (spt/spy-middleware dispatch-spy)
                  {:keys [dispatch]} (collaj/create-store reducer-spy collaj.en/with-keyword-dispatch spy-mw)]
                (dispatch [:some-type])
                (is (spy/called-with? dispatch-spy [:some-type]))
                (is (spy/called-with? reducer-spy nil [:some-type]))))))

(deftest with-fn-dispatch-test
    (testing "with-fn-dispatch"
        (let [reducer-spy (spy/create-spy)
              action-spy (spy/create-spy)
              dispatch-spy (spy/create-spy)
              spy-mw (spt/spy-middleware dispatch-spy)]
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
    (testing "with-log-middleware"
        (testing "creates a middleware with a log-fn that is called with an action being dispatched"
            (let [log-spy (spy/create-spy)
                  log-mw (collaj.en/with-log-middleware log-spy)
                  {:keys [dispatch get-state]} (collaj/create-store (constantly 13) 0 log-mw)]
                (dispatch [:some-type])
                (is (spy/called-times? log-spy 2))
                (is (spy/called-with? log-spy [:some-type]))
                (is (spy/called-with? log-spy 13))))
        (testing "can take two different log-fns"
            (let [log-action-spy (spy/create-spy)
                  log-after-spy (spy/create-spy)
                  log-mw (collaj.en/with-log-middleware log-action-spy log-after-spy)
                  {:keys [dispatch get-state]} (collaj/create-store (constantly 13) 0 log-mw)]
                (dispatch [:some-type])
                (is (spy/called-times? log-action-spy 1))
                (is (spy/called-with? log-action-spy [:some-type]))
                (is (spy/called-times? log-after-spy 1))
                (is (spy/called-with? log-after-spy 13))))))

(defn run [] (run-tests))
