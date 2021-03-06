# Collaj

An extensible state management system for Clojure/ClojureScript inspired by the [Redux](https://github.com/reactjs/redux)
library for Javascript.

## How to use it

Include in your `project.clj`'s dependencies':
```clojure
[com.ben-allred/collaj "0.6.1"]
```

### `create-store`

Creates a new store from a reducer function. `create store` takes an optional `initial-state` and zero or more
`ehancers`. If `initial-state` is not supplied, your reducer function must supply initial state via a zero argument arity.

```clojure
(ns my-namespace.core
    (:require [com.ben-allred.collaj.core :as collaj]))

(defn my-reducer [state [type data]]
    (case type
        :add-to-list (conj state data)
        state))

(def store (collaj/create-store my-other-reducer []))

(defn my-other-reducer
    ([] 0) ;; initial state
    ([state action]
        (case (first action)
            :inc (inc state)
            :dec (dec state)
            state)))

(def other-store (collaj/create-store my-reducer))
```

The resulting store has two functions: `:get-state` which returns the current value of the store and `:dispatch` which
causes your reducer to produce a new state based on the current state and the value dispatched. The value dispatched must
be a sequential clojure data structure (vector list or seq) and the first element in the dispatched value must be a
keyword. The keyword represents the dispatched value "type".

```clojure
(ns my-namespace.core
    (:require [com.ben-allred.collaj.core :as collaj]))

(defn my-reducer [state [type]]
    (case type
        :inc (inc state)
        :dec (dec state)
        state))

(let [{:keys [dispatch get-state]} (collaj/create-store my-reducer 0)]
    (println (get-state))
    (dispatch [:inc])
    (println (get-state))
    (dispatch [:inc])
    (dispatch [:inc])
    (dispatch [:inc])
    (println (get-state))
    (dispatch [:dec])
    (println (get-state)))
;; 0
;; 1
;; 4
;; 3
;; => nil
```

## Reducers

The Reducing function passed to `create-store` can be composed using functions in the `com.ben-allred.collaj.reducers`
namespace.

### `combine`

The `combine` function takes a map of keys to reducers and returns a new reducer whose state is generated and
updated by calling the supplied reducers and collected into a map with the same keys as the map passed in.

```clojure
(ns my-namespace.core
    (:require [com.ben-allred.collaj.core :as collaj]
              [com.ben-allred.collaj.reducers :as collaj.red]))

(defn counter
    ([] 0)
    ([state _] (inc state)))

(defn list-of-things
    ([] [])
    ([state [type data]]
     (case type
         :add-to-list (conj state data)
         state)))

(def my-reducer (collaj.red/combine {:counter counter :list-of-things list-of-things}))

(let [{:keys [dispatch get-state]} (collaj/create-store my-reducer)]
    (println (get-state))
    (dispatch [:anything])
    (println (get-state))
    (dispatch [:add-to-list :some-value])
    (dispatch [:add-to-list :some-other-value])
    (println (get-state)))
;; {:counter 0 :list-of-things []}
;; {:counter 1 :list-of-things []}
;; {:counter 3 :list-of-things [:some-value :some-other-value]}
;; => nil
```

### `assoc`

The `assoc` function takes an initial reducer (that presumably always returns a map) and assoc's keys and values
by calling each reducer with its isolated part of the state and the action. Any part of the state not assoc'ed with
a specific reducer gets passed to the initial reducer.

```clojure
(ns my-namespace.core
    (:require [com.ben-allred.collaj.core :as collaj]
              [com.ben-allred.collaj.reducers :as collaj.red]))

(defn person
    ([] {})
    ([state [type person]]
     (case type
        :set-person person
        state)))

(defn friends
    ([] #{})
    ([state [type friend]]
     (case type
        :add-friend (conj state friend)
        :add-frienemy (conj state friend)
        state)))

(defn enemies
    ([] #{})
    ([state [type enemy]]
     (case type
        :add-enemy (conj state enemy)
        :add-frienemy (conj state enemy)
        state)))

(def my-reducer (collaj.red/assoc person :friends friends :enemies enemies))

(let [{:keys [dispatch get-state]} (collaj/create-store my-reducer)]
    (println (get-state))
    (dispatch [:set-person {:name "Jan"}])
    (println (get-state))
    (dispatch [:add-friend "Bill"])
    (dispatch [:add-enemy "Jax"])
    (println (get-state))
    (dispatch [:set-person {:name "Simon" :favorite-colors #{:yellow :teal} :friends :i-get-assoc'ed-over}])
    (println (get-state))
    (dispatch [:add-friend "Susan"])
    (dispatch [:add-frienemy "Harper"])
    (println (get-state)))
;; {:friends #{} :enemies #{}}
;; {:name "Jan" :friends #{} :enemies #{}}
;; {:name "Jan" :friends #{"Bill"} :enemies #{"Jax"}}
;; {:name "Simon" :favorite-colors #{:yellow :teal} :friends #{"Bill"} :enemies #{"Jax"}}
;; {:name "Simon" :favorite-colors #{:yellow :teal} :friends #{"Susan" "Bill" "Harper"} :enemies #{"Jax" "Harper"}}
;; => nil
```

### `assoc-in`

The `assoc-in` function takes an initial reducer (that presumably always returns a map) and assoc's in key paths and
values by calling each reducer with its isolated part of the state and the action. Any part of the state not assoc'ed
in with a specific reducer gets passed to the initial reducer.

```clojure
(ns my-namespace.core
    (:require [com.ben-allred.collaj.core :as collaj]
              [com.ben-allred.collaj.reducers :as collaj.red]))

(defn person
    ([] {})
    ([state [type person]]
     (case type
        :set-person person
        state)))

(defn friends
    ([] #{})
    ([state [type friend]]
     (case type
        :add-friend (conj state friend)
        :add-frienemy (conj state friend)
        state)))

(defn enemies
    ([] #{})
    ([state [type enemy]]
     (case type
        :add-enemy (conj state enemy)
        :add-frienemy (conj state enemy)
        state)))

(def my-reducer (collaj.red/assoc-in person [:best :friends] friends [:worst :enemies] enemies))

(let [{:keys [dispatch get-state]} (collaj/create-store my-reducer)]
    (println (get-state))
    (dispatch [:set-person {:name "Jan"}])
    (println (get-state))
    (dispatch [:add-friend "Bill"])
    (dispatch [:add-enemy "Jax"])
    (println (get-state))
    (dispatch [:set-person {:name "Simon" :favorite-colors #{:yellow :teal} :best {:friends :i-get-assoc'ed-over}}])
    (println (get-state))
    (dispatch [:add-friend "Susan"])
    (dispatch [:add-frienemy "Harper"])
    (println (get-state)))
;; {:worst {:enemies #{}} :best {:friends #{}}}
;; {:name "Jan" :worst {:enemies #{}} :best {:friends #{}}}
;; {:name "Jan" :worst {:enemies #{"Jax"}} :best {:friends #{"Bill"}}}
;; {:name "Simon" :favorite-colors #{:yellow :teal} :best {:friends #{"Bill"}} :worst {:enemies #{"Jax"}}}
;; {:name "Simon" :favorite-colors #{:yellow :teal} :best {:friends #{"Susan" "Bill" "Harper"}} :worst {:enemies #{"Jax" "Harper"}}}
;; => nil
```

### `map-of`

The `map-of` function takes a key-fn and a reducer and builds a map of non-nil results of calling `(key-fn action)` by
passing their state into the supplied reducer.

```clojure
(ns my-namespace.core
    (:require [com.ben-allred.collaj.core :as collaj]
              [com.ben-allred.collaj.reducers :as collaj.red]))

(defn reducer
    ([] 0)
    ([state [type]]
     (case type
         :add (+ state value)
         :subtract (- state value)
         state)))

(let [{:keys [get-state dispatch]} (collaj/create-store (collaj.red/map-of (comp :key second) reducer))]
    (println (get-state))
    (dispatch [:add {:key :number-1}])
    (dispatch [:subtract {:key :number-2}])
    (dispatch [:add {:key :number-2}])
    (dispatch [:another-type {:key :number-1}])
    (dispatch [:subtract {}])
    (println (get-state)))
;; nil
;; {:number-1 1 :number-2 0}
;; => nil
```

### `comp`

The `comp` function composes reducers into a single reducer producing the new state by threading it through
every reducer from right to left. Only the first reducer is used to produce initial state.

```clojure
(ns my-namespace.core
    (:require [com.ben-allred.collaj.core :as collaj]
              [com.ben-allred.collaj.reducers :as collaj.red]))

(defn reducer-1
    ([] #{})
    ([state [type value]]
     (case type
        :remove (disj state value)
        state))

(defn reducer-2
    ([] ::never-gonna-see-me)
    ([state [type value]]
     (case type
        :add (conj state value)
        state))

(let [{:keys [get-state dispatch]} (collaj/create-store (collaj.red/comp reducer-1 reducer-2))]
    (println (get-state))
    (dispatch [:add :some-value])
    (dispatch [:add :some-other-value])
    (dispatch [:remove :some-value])
    (println (get-state)))
;; #{}
;; #{:some-other-value}
;; => nil
```

## Enhancers

Enhancers exist in the `com.ben-allred.collaj.enhancers` namespace. These can be mixed and matched when creating your store by
passing any of them as additional arguments to `create-store`. The resulting enhancements happen in the order they are
passed to `create-store`. Typically, you'll need enhancers that alter the data being dispatched to be first in the list
(such as `with-fn-dispatch` and `with-keyword-dispatch`). You will likely want `with-log-middleware` last - or at least
*after* `with-fn-dispatch` - because you may want to see the actions that are actually hitting your reducer and probably
don't need to see functions stringified in your logs. Keep this in mind when using these (or other) store enhancers.

### `with-subscribers`

The `with-subscribers` enhancer adds a `:subscribe` function to the store which allows you to supply one or more
functions to subscribe to changes to the store. The `:subscribe` function can optionally take a `type-fn` which can
filter out which types your subscription is notified about. `:subscribe` returns a zero arity function that will
cancel the subscription.

```clojure
(ns my-namespace.core
    (:require [com.ben-allred.collaj.core :as collaj]
              [com.ben-allred.collaj.enhancers :as collaj.en]))

(def store (collaj/create-store my-reducer collaj.en/with-subscribers))

(def dispatch (:dispatch store))

(def subscribe (:subscribe store))

(subscribe (fn [action] (println "all actions" action)))

(def unsubscribe-ab
    (subscribe #{:type-a :type-b}
        (fn [action] (println "only types a and b" action))))

(dispatch [:type-a])
;; "all actions [:type-a]"
;; "only-types a and b [:type-a]"
(dispatch [:type-b {:some :data}])
;; "all actions [:type-a {:some :data}]"
;; "only-types a and b [:type-a {:some :data}]"
(dispatch [:type-c])
;; "all actions [:type-c]"
(unsubscribe-ab)
(dispatch [:type-a])
;; "all actions [:type-a]"
```

### `with-watchers`

This enhancer adds a `:watch` function to the store which takes a path from the root of your state tree and a function to
be invoked when any nested value changes at the supplied path. `:watch` returns an `:unwatch` function to be called when
watching the state is no longer needed.

```clojure
(ns my-namespace.core
    (:require [com.ben-allred.collaj.core :as collaj]
              [com.ben-allred.collaj.enhancers :as collaj.en]))

(defn my-reducer
    ([]
     {:numbers #{} :letters {:lower-case #{} :upper-case #{}}})
    ([state [type value]]
     (case type
        :numbers/add (update state :numbers conj value)
        :letters.lower-case/add (update-in state [:letters :lower-case] conj value)
        :letters.upper-case/add (update-in state [:letters :upper-case] conj value)
        state)))

(def store (collaj/create-store my-reducer collaj.en/with-watchers))

(def dispatch (:dispatch store))

(def watch (:watch store))

(watch [:numbers] (fn [old-value new-value]
                      (println "numbers are now" new-value)))

(watch [:letters :lower-case] (fn [old-value new-value]
                                  (println "lower-case letters are now" new-value)))

(def unwatch-root
    (watch [] (fn [old-value new-value]
                  (println "entire state was" old-value)
                  (println "entire state is" new-value))))

(dispatch [:numbers/add 3])
;; numbers are now #{3}
;; entire state was {:numbers #{}, :letters {:lower-case #{}, :upper-case #{}}}
;; entire state is {:numbers #{3}, :letters {:lower-case #{}, :upper-case #{}}}
(dispatch [:letters.lower-case/add "z"])
;; lower-case letters are now #{z}
;; entire state was {:numbers #{3}, :letters {:lower-case #{}, :upper-case #{}}}
;; entire state is {:numbers #{3}, :letters {:lower-case #{z}, :upper-case #{}}}
(dispatch [:letters.upper-case/add "L"])
;; entire state was {:numbers #{3}, :letters {:lower-case #{z}, :upper-case #{}}}
;; entire state is {:numbers #{3}, :letters {:lower-case #{z}, :upper-case #{L}}}
(dispatch [:letters.lower-case/add "z"]) ;; does not change state
(unwatch-root)
(dispatch [:letters.lower-case/add "r"])
;; lower-case letters are now #{z r}
```

### `with-keyword-dispatch`

This allows you to dispatch keywords and will wrap them into a vector before getting to your reducer.

```clojure
(ns my-namespace.core
    (:require [com.ben-allred.collaj.core :as collaj]
              [com.ben-allred.collaj.enhancers :as collaj.en]))

(defn my-reducer
    ([] nil)
    ([state action]
        action)

(def store (collaj/create-store my-reducer collaj.en/with-keyword-dispatch))

((:dispatch store) :do-something)
((:get-state store))
;; => [:do-something]
```

### `with-fn-dispatch`

This allows you to dispatch functions that gets invoked with `dispatch` and `get-state` (in a vector).

```clojure
(ns my-namespace.core
    (:require [com.ben-allred.collaj.core :as collaj]
              [com.ben-allred.collaj.enhancers :as collaj.en]))

(def store (collaj/create-store (constantly 13) collaj.en/with-fn-dispatch))

((:dispatch store) (fn overflow-the-stack [[dispatch get-state]]
                        (if (= (get-state) 14)
                            (dispatch [:didn't-blow-up])
                            (dispatch overflow-the-stack))))
;; StackOverflowError
```

### `with-chan-dispatch`

This allows you to dispatch a `core.async/chan`. Values placed on the channel will be dispatched as they happen until
the channel is closed.

```clojure
(ns my-namespace.core
    (:require [com.ben-allred.collaj.core :as collaj]
              [clojure.core.async :as async]
              [com.ben-allred.collaj.enhancers :as collaj.en]))

(def store (collaj/create-store my-reducer 13 collaj.en/with-chan-dispatch))

(def dispatch-chan (async/chan))
((:dispatch store) dispatch-chan)

(async/go
    (async/>! dispatch-chan [:some-event {:some :data}])
    (async/>! dispatch-chan [:some-othe-event {:some [:other :data]}]))
```

### `with-log-middleware`

A middleware for "peeking" at the action being dispatched and the resulting state.

```clojure
(ns my-namespace.core
    (:require [com.ben-allred.collaj.core :as collaj]
              [com.ben-allred.collaj.enhancers :as collaj.en]))

(def middleware (rdex/with-log-middleware
                    (comp log/debug (partial str "dispatching action: "))
                    (comp log/debug (partial str "updated state is: "))

(def store (collaj/create-store my-reducer 13 middleware))
```

### Custom Enhancer

You can make your own custom enhancer. An enhancer is a function that accepts the `next` enhancer in the middleware
chain and returns a function that accepts the `reducer` and `initial-state` and returns a store.

```clojure
(defn do-nothing-enhancer [next]
    (fn [reducer initial-state]
        (next reducer initial-state)))

(defn hijack-enahncer [next]
    (fn [reducer initial-state]
        (assoc (next (constantly :muaaahahahaha!) nil) :favorite-number 17)))
```

## Dispatch Middleware

There is an `apply-middleware` function in the core namespace that takes one or more dispatch middleware functions and
creates an enhancer to be used when creating your store. A dispatch middleware is a function that accepts the `get-state`
function and returns a function that accepts the next middleware in the dispatch middleware chain, and returns a new
dispatching function.

```clojure
(defn do-nothing-dispatch-middleware [get-state]
    (fn [next]
        (fn [action]
            (next action))))

(defn hijack-dispatch-middleware [get-state]
    (fn [next]
        (fn [action]
            (next [:hijacked-action]))))

(def enhancer (collaj/apply-middleware
                  do-nothing-dispatch-middleware
                  hijack-dispatch-middleware))
```

## Local store

There is a `create-local-store` function which works the same as `create-store` accept it takes another `dispatch`
function as the first argument. The resulting store will give you a new `dispatch` function which will interact with
your local store and also call the supplied `dispatch` function. Consider using the same middleware for both stores if
you want to be able to dispatch values that are not in the default format.

```clojure
(def store (collaj/create-store reducer))

(def local-store (:dispatch store) local-reducer)

;; calling (:dispatch local-store) will also call (:dispatch store) with the same value
```

## Custom Store and Custom Local Store

There are also a `create-custom-store` and `create-custom-local-store` functions which work the same way, except that
they take an additional first argument that will be used to create the ref value that generates the store. If you are
interested in using a custom ref type other than `clojure.core/atom`, use these functions to create your store.

```clojure
(def store (collaj/create-custom-store some.other.ref/function reducer initial-state enhancer))
(def sub-store (collaj/create-custom-local-store some.other.ref/function (:dispatch store) reducer))
```
