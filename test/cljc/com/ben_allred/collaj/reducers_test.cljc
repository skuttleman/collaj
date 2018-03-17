(ns com.ben-allred.collaj.reducers-test
    (:require #?(:clj [clojure.test :refer [deftest testing is are run-tests]]
                 :cljs [cljs.test :refer-macros [deftest testing is are run-tests]])
                      [com.ben-allred.collaj.core :as collaj]
                      [com.ben-allred.collaj.reducers :as collaj.red]
                      [support.spies :as spy]))

(deftest combine-test
    (let [counter  (fn ([] 0) ([state action] (if (= action :counter) (inc state) state)))
          c-cat    (fn ([] "") ([state action] (if (= action :c-cat) (str state (count state)) state)))
          l-action (fn ([] nil) ([state action] action))]
        (testing "(combine)"
            (let [reducer (collaj.red/combine {:counter counter :c-cat c-cat :l-action l-action})]
                (testing "returns initial state of reducers"
                    (are [actual expected] (= actual expected)
                        (reducer) {:counter 0 :c-cat "" :l-action nil}))
                (testing "applies changes to individual reducers"
                    (are [actual expected] (= actual expected)
                        (reducer {:counter 0 :c-cat "" :l-action nil} :counter) {:counter 1 :c-cat "" :l-action :counter}
                        (reducer {:counter 0 :c-cat "0123" :l-action :counter} :c-cat) {:counter 0 :c-cat "01234" :l-action :c-cat}
                        (reducer {:counter 0 :c-cat "0" :l-action :c-cat} :unknown) {:counter 0 :c-cat "0" :l-action :unknown}))))))

(deftest assoc-test
    (let [person   (spy/spy-on (fn ([] {:first-name "" :last-name ""})
                                   ([state [type value]]
                                    (if (= :person/update type)
                                        (select-keys value [:first-name :last-name])
                                        state))))
          fav-nums (spy/spy-on (fn ([] #{})
                                   ([state [type num]]
                                    (case type
                                        :nums/like (conj state num)
                                        :nums/dislike (disj state num)
                                        state))))
          injuries (spy/spy-on (fn ([] [])
                                   ([state [type injury]]
                                    (if (= type :injury/sustain)
                                        (conj state injury)
                                        state))))]
        (testing "(assoc)"
            (let [reducer (collaj.red/assoc person :fav-nums fav-nums :injuries injuries)]
                (testing "returns initial state of reducers"
                    (is (= (reducer) {:first-name "" :last-name "" :fav-nums #{} :injuries []})))
                (testing "calls reducers with isolated state"
                    (spy/reset-spies!)
                    (reducer {:arbitrary :thing :first-name "Jimmy" :last-name "Joe" :fav-nums #{13} :injuries [:knee]} [:some-event])
                    (are [spy state] (spy/called-with? spy state [:some-event])
                        person {:arbitrary :thing :first-name "Jimmy" :last-name "Joe"}
                        fav-nums #{13}
                        injuries [:knee]))
                (testing "applies changes to individual reducers"
                    (are [state action expected] (= (reducer state action) expected)
                        {:first-name "" :last-name "" :fav-nums #{13} :injuries [:knee]}
                        [:person/update {:first-name "Jimmy" :last-name "Joe" :fav-nums #{-9} :injuries [:face]}]
                        {:first-name "Jimmy" :last-name "Joe" :fav-nums #{13} :injuries [:knee]}

                        {:first-name "" :last-name "" :fav-nums #{13} :injuries [:knee]}
                        [:nums/like 7]
                        {:first-name "" :last-name "" :fav-nums #{13 7} :injuries [:knee]}

                        {:first-name "" :last-name "" :fav-nums #{13} :injuries [:knee]}
                        [:nums/dislike 13]
                        {:first-name "" :last-name "" :fav-nums #{} :injuries [:knee]}

                        {:first-name "" :last-name "" :fav-nums #{13} :injuries [:knee]}
                        [:injury/sustain :feelings]
                        {:first-name "" :last-name "" :fav-nums #{13} :injuries [:knee :feelings]})))
            (let [reducer (collaj.red/assoc (constantly nil) :fav-nums fav-nums)]
                (testing "defaults initial reducer to empty map when it returns nil"
                    (is (= (reducer) {:fav-nums #{}}))
                    (is (= (reducer {:fav-nums #{} :something :else} [:nums/like 75]) {:fav-nums #{75}})))))
        (testing "(assoc-in)"
            (let [reducer (collaj.red/assoc-in person [:fav-nums :joey] fav-nums [:fav-nums :billy] fav-nums [:injuries] injuries)]
                (testing "returns initial state of reducers"
                    (is (= (reducer) {:first-name "" :last-name "" :fav-nums {:joey #{} :billy #{}} :injuries []})))
                (testing "calls reducers with isolated state"
                    (spy/reset-spies!)
                    (reducer {:arbitrary :thing :first-name "Jimmy" :last-name "Joe" :fav-nums {:billy #{13} :joey #{37}} :injuries [:knee]} [:some-event])
                    (are [spy state] (spy/called-with? spy state [:some-event])
                        person {:arbitrary :thing :first-name "Jimmy" :last-name "Joe" :fav-nums {}}
                        fav-nums #{13}
                        fav-nums #{37}
                        injuries [:knee]))
                (testing "applies changes to individual reducers"
                    (are [state action expected] (= (reducer state action) expected)
                        {:first-name "" :last-name "" :fav-nums {:billy #{13} :joey #{37}} :injuries [:knee]}
                        [:person/update {:first-name "Jimmy" :last-name "Joe" :fav-nums #{-9} :injuries [:face]}]
                        {:first-name "Jimmy" :last-name "Joe" :fav-nums {:billy #{13} :joey #{37}} :injuries [:knee]}

                        {:first-name "" :last-name "" :fav-nums {:billy #{13} :joey #{37}} :injuries [:knee]}
                        [:nums/like 7]
                        {:first-name "" :last-name "" :fav-nums {:billy #{13 7} :joey #{37 7}} :injuries [:knee]}

                        {:first-name "" :last-name "" :fav-nums {:billy #{13} :joey #{37}} :injuries [:knee]}
                        [:nums/dislike 13]
                        {:first-name "" :last-name "" :fav-nums {:billy #{} :joey #{37}} :injuries [:knee]}

                        {:first-name "" :last-name "" :fav-nums {:billy #{13} :joey #{37}} :injuries [:knee]}
                        [:injury/sustain :feelings]
                        {:first-name "" :last-name "" :fav-nums {:billy #{13} :joey #{37}} :injuries [:knee :feelings]})))
            (let [reducer (collaj.red/assoc-in (constantly nil) [:some :path :to :fav-nums] fav-nums)]
                (testing "defaults initial reducer to empty map when it returns nil"
                    (is (= (reducer) {:some {:path {:to {:fav-nums #{}}}}}))
                    (is (= (reducer {:some {:path {:to {:fav-nums #{}}}} :something :else} [:nums/like 75])
                            {:some {:path {:to {:fav-nums #{75}}}}})))))))

(deftest map-of-test
    (testing "(map-of)"
        (let [key-fn  (comp :key second)
              reducer (spy/spy-on (fn
                                      ([] 0)
                                      ([state [_ {:keys [value]}]]
                                       (+ state value))))]
            (testing "defaults to nil"
                (let [get-state (:get-state (collaj/create-store (collaj.red/map-of key-fn reducer)))]
                    (is (= {} (get-state)))))
            (testing "adds key to map"
                (spy/reset-spy! reducer)
                (let [{:keys [get-state dispatch]} (collaj/create-store (collaj.red/map-of key-fn reducer))
                      action [:any-type {:key ::some-key :value 1}]]
                    (dispatch action)
                    (is (= (get-state) {::some-key 1}))
                    (is (spy/called-with? reducer))
                    (is (spy/called-with? reducer 0 action))))
            (testing "updated value in map"
                (spy/reset-spy! reducer)
                (let [{:keys [get-state dispatch]} (collaj/create-store (collaj.red/map-of key-fn reducer) {::some-key -3})
                      action [:any-type {:key ::some-key :value 1}]]
                    (dispatch action)
                    (is (= (get-state) {::some-key -2}))
                    (is (spy/called-times? reducer 1))
                    (is (spy/called-with? reducer -3 action))))
            (testing "treats false as an acceptable key"
                (let [{:keys [get-state dispatch]} (collaj/create-store (collaj.red/map-of key-fn reducer) {false -3})
                      action [:any-type {:key false :value 4}]]
                    (dispatch action)
                    (is (= (get-state) {false 1}))))
            (testing "keeps unchanged key / values"
                (let [{:keys [get-state dispatch]} (collaj/create-store (collaj.red/map-of key-fn reducer))]
                    (dispatch [:any-type {:key ::key-1 :value 7}])
                    (dispatch [:any-type {:key ::key-2 :value 7}])
                    (dispatch [:any-type {:key ::key-1 :value 7}])
                    (is (= (get-state) {::key-1 14 ::key-2 7})))))))

(deftest comp-test
    (testing "(comp)"
        (testing "returns no-op reducer when called with no reducers"
            (let [reducer (collaj.red/comp)]
                (is (nil? (reducer)))
                (is (= ::any-random-value (reducer ::any-random-value [:some :sort :of :action])))))
        (testing "return reducer when called with one reducer"
            (is (identical? identity (collaj.red/comp identity))))
        (testing "when composing two reducers"
            (let [reducer-1 (spy/spy-on (constantly 17))
                  reducer-2 (spy/spy-on (constantly 3))
                  reducer (collaj.red/comp reducer-1 reducer-2)]
                (testing "calling with no args calls only the first reducer"
                    (spy/reset-spy! reducer-1)
                    (spy/reset-spy! reducer-2)
                    (let [result (reducer)]
                        (is (spy/called-with? reducer-1))
                        (is (spy/called-times? reducer-1 1))
                        (is (spy/never-called? reducer-2))
                        (is (= 17 result))))
                (testing "calling with state and action calls both reducers"
                    (spy/reset-spy! reducer-1)
                    (spy/reset-spy! reducer-2)
                    (let [result (reducer ::some-state [:any :action])]
                        (is (spy/called-with? reducer-2 ::some-state [:any :action]))
                        (is (spy/called-times? reducer-2 1))
                        (is (spy/called-with? reducer-1 3 [:any :action]))
                        (is (spy/called-times? reducer-1 1))
                        (is (= 17 result))))))
        (testing "when composing many reducers"
            (let [reducer-1 (spy/spy-on (constantly 17))
                  rand-ints (take (rand-int 100) (repeatedly #(rand-int 1000)))
                  other-reducers (map (comp spy/spy-on constantly) rand-ints)
                  reducer (apply collaj.red/comp reducer-1 other-reducers)]
                (testing "calling with no args calls only the first reducer"
                    (spy/reset-spy! reducer-1)
                    (dorun (map spy/reset-spy! other-reducers))
                    (let [result (reducer)]
                        (is (spy/called-with? reducer-1))
                        (is (spy/called-times? reducer-1 1))
                        (is (every? spy/never-called? other-reducers))
                        (is (= 17 result))))
                (testing "calling with state and action calls both reducers"
                    (spy/reset-spy! reducer-1)
                    (dorun (map spy/reset-spy! other-reducers))
                    (let [result (reducer ::some-state [:any :action])
                          all-reducers (cons reducer-1 other-reducers)
                          states (conj (vec rand-ints) ::some-state)]
                        (is (every? #(spy/called-times? % 1) all-reducers))
                        (dorun (map #(is (spy/called-with? %1 %2 [:any :action]))
                                   all-reducers
                                   states))
                        (is (= 17 result))))))))
