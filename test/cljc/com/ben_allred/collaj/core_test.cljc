(ns com.ben-allred.collaj.core-test
    (:require #?(:clj [clojure.test :refer [deftest testing is are run-tests]]
                 :cljs [cljs.test :refer-macros [deftest testing is are run-tests]])
                      [com.ben-allred.collaj.core :as collaj]
                      [support.spies :as spy]))

(deftest create-store-test
    (let [reducer (fn ([] 0) ([s a] (inc s)))
          doubler (fn [create] (fn [reducer initial]
                                   (create (fn [state action] (* 2 (reducer state action))) initial)))
          +five   (fn [create] (fn [reducer initial]
                                   (create (fn [state action] (+ 5 (reducer state action))) initial)))]
        (testing "(create-store)"
            (testing "returns a store"
                (let [store (collaj/create-store reducer)]
                    (are [actual expected] (= actual expected)
                        (fn? (:dispatch store)) true
                        (fn? (:get-state store)) true)))

            (testing "has fn :get-state which returns value from reducer"
                (let [store (collaj/create-store reducer)]
                    (is (= ((:get-state store)) 0))))

            (testing "takes initial-state which overrides reducer"
                (let [store (collaj/create-store reducer 3)]
                    (is (= ((:get-state store)) 3))))

            (testing "has fn :dispatch which updates state"
                (let [r-spy (spy/spy-on reducer)
                      {:keys [dispatch get-state]} (collaj/create-store r-spy)]
                    (is (spy/called-with? r-spy))
                    (dispatch [:anything])
                    (dispatch [:at-all])
                    (is (spy/called-with? r-spy 0 [:anything]))
                    (is (spy/called-with? r-spy 1 [:at-all]))
                    (is (= (get-state) 2))))

            (testing "has fn :dispatch which validates dispatched value"
                (let [{:keys [dispatch]} (collaj/create-store reducer)]
                    (are [undispatchable] (thrown? #?(:clj Exception :cljs js/Object) (dispatch undispatchable))
                        :wrong
                        "bad"
                        ["still bad"]
                        {:never :ever})))

            (testing "can be enhanced"
                (testing "by passing an enhancer"
                    (let [store (collaj/create-store reducer doubler)
                          {:keys [get-state dispatch]} store]
                        (is (= (get-state) 0))
                        (dispatch [:anything])
                        (is (= (get-state) 2))
                        (dispatch [:anything])
                        (is (= (get-state) 6))
                        (dispatch [:anything])
                        (is (= (get-state) 14))))

                (testing "by passing multiple enhancers which are applied left to right"
                    (let [store (collaj/create-store reducer +five doubler)
                          {:keys [get-state dispatch]} store]
                        (is (= (get-state) 0))
                        (dispatch [:anything])
                        (is (= (get-state) 12))
                        (dispatch [:anything])
                        (is (= (get-state) 36))
                        (dispatch [:anything])
                        (is (= (get-state) 84))))

                (testing "by passing enhancers and an initial-state"
                    (let [store (collaj/create-store reducer -9 +five doubler)
                          {:keys [get-state dispatch]} store]
                        (is (= (get-state) -9))
                        (dispatch [:anything])
                        (is (= (get-state) -6))
                        (dispatch [:anything])
                        (is (= (get-state) 0))
                        (dispatch [:anything])
                        (is (= (get-state) 12))))))))

(deftest create-custom-store-test
    (testing "(create-custom-store)"
        (testing "takes custom atom fn"
            (let [atm (spy/spy-on atom)
                  _   (collaj/create-custom-store atm (constantly 17) :initial-state)]
                (is (spy/called-with-times? atm 1 :initial-state))))))

(deftest enhance-reducer-test
    (testing "(enhance-reducer)"
        (testing "uses return value as reducer"
            (let [enhancer (collaj/enhance-reducer (fn [& _] (constantly 17)))
                  {:keys [dispatch get-state]} (collaj/create-store (constantly 3) enhancer)]
                (dispatch [:some-action])
                (is (= (get-state) 17))))
        (testing "can handle multiple enhancers"
            (let [enhancer  #(fn [s a] (inc (% s a)))
                  enhancers (collaj/enhance-reducer enhancer enhancer enhancer)
                  {:keys [dispatch get-state]} (collaj/create-store (constantly 3) enhancers)]
                (dispatch [:some-action])
                (is (= (get-state) 6))))
        (testing "ignores nil enhancers"
            (let [enhancer (collaj/enhance-reducer (fn [reducer] (fn [state [type :as action]]
                                                                     (if (= :specific-type type)
                                                                         17
                                                                         (reducer state action)))))
                  {:keys [dispatch get-state]} (collaj/create-store (constantly 3) 0 nil enhancer nil)]
                (is (= (get-state) 0))
                (dispatch [:specific-type])
                (is (= (get-state) 17))
                (dispatch [:random-type])
                (is (= (get-state) 3))))))

(deftest apply-middleware-test
    (let [any-type (fn [value] [:any-type {:value value}])]
        (testing "(apply-middleware)"
            (testing "uses return value as dispatch"
                (let [mw      (fn [_] (fn [next] (fn [_] (next [:hijacked-action]))))
                      applied (collaj/apply-middleware mw)
                      {:keys [dispatch get-state]} (collaj/create-store (fn [_ [type]] type) nil applied)]
                    (dispatch [:action])
                    (is (= (get-state) :hijacked-action))))
            (testing "can handle multiple enhancers"
                (let [mw      (fn [_] (fn [next] (fn [[_ {value :value}]]
                                                     (next (any-type (* value value))))))
                      applied (collaj/apply-middleware mw mw mw)
                      {:keys [dispatch get-state]} (collaj/create-store (fn [_ [_ {value :value}]] value) nil applied)]
                    (dispatch (any-type 2))
                    (is (= (get-state) 256))))
            (testing "can use get-state"
                (let [mw      (fn [get-state] (fn [next] (fn [_] (next (any-type (get-state))))))
                      applied (collaj/apply-middleware mw)
                      {:keys [dispatch get-state]} (collaj/create-store (fn [_ [_ {value :value}]] value) 3 applied)]
                    (dispatch (any-type 17))
                    (is (= (get-state) 3)))))))

(deftest create-local-store-test
    (let [spy     (spy/create-spy)
          reducer (fn [state [type :as action]]
                      (spy action)
                      (case type :reset! 0 (dec state)))
          {dispatch' :dispatch get-state' :get-state} (collaj/create-store reducer 0)]
        (testing "(create-local-store)"
            (testing "behaves like a store"
                (let [{:keys [dispatch get-state]} (collaj/create-local-store dispatch' (fn [state _] (inc state)) 0)]
                    (dispatch [:type])
                    (is (= 1 (get-state)))
                    (dispatch [:type])
                    (is (= 2 (get-state)))))
            (testing "calls parent store's dispatch"
                (dispatch' [:reset!])
                (spy/reset-spy! spy)
                (let [{:keys [dispatch get-state]} (collaj/create-local-store dispatch' (fn [state _] (inc state)) 0)]
                    (dispatch [:type])
                    (is (= -1 (get-state')))
                    (dispatch [:type])
                    (is (= -2 (get-state')))
                    (is (spy/called-with-times? spy 2 [:type])))))
        (testing "(create-custom-local-store)"
            (testing "takes custom atom fn"
                (let [atm (spy/spy-on atom)
                      _   (collaj/create-custom-local-store atm dispatch' (constantly 17) :initial-state)]
                    (is (spy/called-with-times? atm 1 :initial-state)))))))
