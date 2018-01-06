(ns com.ben-allred.collaj.reducers-test
    (:require [cljs.test :refer-macros [deftest testing is are run-tests use-fixtures]]
              [com.ben-allred.collaj.core :as collaj]
              [com.ben-allred.collaj.reducers :as collaj.red]
              [support.spies :as spy]
              [support.core :as spt]))

(deftest combine-reducers-test
    (let [counter  (fn ([] 0) ([state action] (if (= action :counter) (inc state) state)))
          c-cat    (fn ([] "") ([state action] (if (= action :c-cat) (str state (count state)) state)))
          l-action (fn ([] nil) ([state action] action))]
        (testing "(combine-reducers)"
            (let [reducer (collaj.red/combine-reducers {:counter counter :c-cat c-cat :l-action l-action})]
                (testing "returns initial state of reducers"
                    (are [actual expected] (= actual expected)
                        (reducer) {:counter 0 :c-cat "" :l-action nil}))
                (testing "applies changes to individual reducers"
                    (are [actual expected] (= actual expected)
                        (reducer {:counter 0 :c-cat "" :l-action nil} :counter) {:counter 1 :c-cat "" :l-action :counter}
                        (reducer {:counter 0 :c-cat "0123" :l-action :counter} :c-cat) {:counter 0 :c-cat "01234" :l-action :c-cat}
                        (reducer {:counter 0 :c-cat "0" :l-action :c-cat} :unknown) {:counter 0 :c-cat "0" :l-action :unknown}))))))
