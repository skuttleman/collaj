(ns com.ben-allred.collaj.reducers-test
    (:require [cljs.test :refer-macros [deftest testing is are run-tests use-fixtures]]
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
                        {:first-name "" :last-name "" :fav-nums #{13} :injuries [:knee :feelings]}))))))

(defn run [] (run-tests))
