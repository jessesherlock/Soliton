(ns soliton.lens-test
  (:require [clojure.test :refer [deftest is]]
            [soliton.core :refer [focus put over]]
            [soliton.lens :as sut])
  #?(:clj (:import [java.util Date])))

#?(:clj (set! *warn-on-reflection* true)
   :cljs (set! *warn-on-infer* true))

(deftest constructors-test
  (let [test-map {:foo 1}
        getter (fn [s] (:foo s))
        setter (fn [s v] (assoc s :foo v))
        updater (fn [s f] (update s :foo f))

        l-2-arg (sut/lens getter setter)
        l-3-arg (sut/lens getter setter updater)
        l-fmap (sut/fmap getter updater)]

    (is (= 1
           (focus l-2-arg test-map)
           (focus l-3-arg test-map)
           (focus l-fmap test-map)))

    (is (= {:foo 42}
           (put l-2-arg 42 test-map)
           (put l-3-arg 42 test-map)
           (put l-fmap 42 test-map)))

    (is (= {:foo 2}
           (over l-2-arg inc test-map)
           (over l-3-arg inc test-map)
           (over l-fmap inc test-map)))))

(deftest iso-test
  (let [millis->date (fn [millis] #?(:clj (Date. (long millis))
                                     :cljs (js/Date. (long millis))))
        date->millis (fn [date] #?(:clj (.getTime ^java.util.Date date)
                                   :cljs (.getTime ^Date date)))
        as-millis (sut/iso date->millis millis->date)]

    (is (= (long 1192579200000)
           (focus as-millis #inst "2007-10-17")))

    (is (= #inst "2007-10-18"
           (put as-millis (+ 1192579200000 86400000) #inst "2007-10-17")))

    (is (= #inst "2007-10-18"
           (over as-millis #(+ % 86400000) #inst "2007-10-17")))))

(deftest const-test
  (is (= 7 (focus (sut/const 7) :x))))

(deftest key-lens-test
  (let [foo (sut/key "foo")
        m {"foo" :foo
           "bar" :bar}]

    (is (= :foo
           (focus foo m)))

    (is (= {"foo" :new
            "bar" :bar}
           (put foo :new m)))

    (is (= {"foo" ":foo"
            "bar" :bar}
           (over foo str m)))))

(deftest passes-test
  (let [l (sut/passes even?)]
    (is (= 2 (focus l 2)))
    (is (= nil (focus l 3)))
    (is (= 100 (put l 100 2)))
    (is (= 3 (put l 100 3)))
    (is (= 3 (over l inc 2)))
    (is (= 1 (over l (fnil inc 1000) 1)))))

(deftest select-keys-test
  (let [l (sut/select-keys [:alpha :bravo])
        test-map {:alpha :a, :bravo :b, :charlie :c, :delta :d}]

    (is (= {:alpha :a, :bravo :b}
           (focus l test-map)))

    (is (= {:charlie :c, :delta :d}
           (put l {} test-map)))

    (is (= {:alpha "a", :bravo "b", :charlie :c, :delta :d}
           (put l {:alpha "a" :bravo "b"} test-map)))

    (is (= {:alpha "a", :bravo "b", :charlie :c, :delta :d}
           (over l
                 (fn [m] (update-vals m name))
                 test-map)))))

(deftest merge-test
  (is (= {:foo 1 :bar 2}
         (focus sut/merge {:foo 1 :bar 2})))

  (is (= {:foo 1 :bar 2 :baz 3}
         (put sut/merge {:baz 3} {:foo 1 :bar 2 :baz :invalid})))

  (is (= {:foo 1 :bar 2 :baz 3}
         (over sut/merge (fn [m] {:baz (apply + (vals m))}) {:foo 1 :bar 2}))))

(deftest deep-merge-test
  (let [test-map {:italy {:capital :rome, :lang :italian}
                  :france {:capital :paris, :lang :french}}]

    (is (= test-map
           (focus sut/deep-merge test-map)))

    (is (= test-map
           (put sut/deep-merge
                {:italy {:lang :italian}
                 :france {:lang :french}}
                {:italy {:capital :rome}
                 :france {:capital :paris}})))

    (is (= test-map
           (put sut/deep-merge
                {:italy {:lang :italian}
                 :france {:lang :french}}
                {:italy {:capital :rome, :lang :english}
                 :france {:capital :paris, :lang :english}})))))

(deftest dissoc-test
  (let [test-map {:foo :bar
                  :bravo 1
                  :alpha {:bravo 2
                          :charlie 1}}]
    (is (= {:foo :bar
            :bravo 1
            :alpha {:charlie 1}}
           (over :alpha #(clojure.core/dissoc % :bravo) test-map)))

    (is (= {:charlie 1}
           (focus [:alpha (sut/dissoc :bravo)] test-map)))

    (is (= {:foo :bar
            :bravo 1
            :alpha {:bravo 2
                    :delta 4}}
           (put [:alpha (sut/dissoc :bravo)] {:delta 4} test-map)))

    (is (= {:foo {:bar 1 :baz 2 :bam 3 :all-keys #{:baz :bam}}}
           (over [:foo (sut/dissoc :bar)]
                 #(assoc % :all-keys (set (keys %)))
                 {:foo {:bar 1, :baz 2, :bam 3}})))))

(deftest idx-test
  (is (= 5
         (focus (sut/idx 4) [1 2 3 4 5 6])))

  (is (= [1 2 3]
         (put (sut/idx 1) 2 [1 nil 3])))

  (is #?(:clj (thrown? IndexOutOfBoundsException (focus (sut/idx 2) [1]))
         :cljs (thrown-with-msg? js/Error #"No item \d in vector of length \d"
                                 (focus (sut/idx 2) [1]))))
  (is #?(:clj (thrown? IndexOutOfBoundsException (put (sut/idx 2) 5 [4]))
         :cljs (thrown-with-msg? js/Error #"Index \d out of bounds"
                                 (put (sut/idx 2) 5 [4])))))

(deftest filled-idx-test
  (is (= :value
         (focus (sut/filled-idx 2) [:x :x :value :y])))

  (is (= [:x :x :value :y]
         (put (sut/filled-idx 2) :value [:x :x :x :y])))

  (is (= [nil nil :x]
         (put (sut/filled-idx 2) :x [])))

  (is (= [:x :x 43 :y]
         (over (sut/filled-idx 2) inc [:x :x 42 :y])))

  (is (= [1]
         (over (sut/filled-idx 0) (fnil inc 0) []))))

(over (sut/filled-idx 2) inc [:x :x 42 :y])

(deftest slice-test
  (is (= [2 3 4]
         (focus (sut/slice 1 4) [1 2 3 4 5])))

  (is (= [1 :x :y 5]
         (put (sut/slice 1 4) [:x :y] [1 2 3 4 5])))

  (is (= [1 2 4 5]
         (over (sut/slice 1 4) #(filter even? %) [1 2 3 4 5]))))

(deftest pop-test
  (is (= [1 2 3 4]
         (focus sut/pop [1 2 3 4 5])))

  (is (= [8 7 6 5]
         (put sut/pop [8 7 6] [1 2 3 4 5])))

  (is (= [2 4 5]
         (over sut/pop #(vec (filter even? %)) [1 2 3 4 5])))

  (is (= [1 2]
         (put sut/pop [1 2] [])))

  (is (= [1 2 3]
         (put sut/pop [1 2] [3]))))

(deftest peek-and-stack-test
  (is (= 5
         (focus sut/peek [1 2 3 4 5])
         (focus sut/stack [1 2 3 4 5])))

  (is (= [1 2 3 4 100]
         (put sut/peek 100 [1 2 3 4 5])))

  (is (= [1 2 3 4 5 100]
         (put sut/stack 100 [1 2 3 4 5])))

  (is (= [1 2 3 4 6]
         (over sut/peek inc [1 2 3 4 5])))

  (is (= [1 2 3 4 5 6]
         (over sut/stack inc [1 2 3 4 5]))))

(deftest first-rest-next-test
  (is (= 1
         (focus sut/first [1 2 3 4 5])))

  (is (= nil
         (focus sut/first [])
         (focus sut/first nil)))

  (is (= [2 3 4 5]
         (focus sut/rest [1 2 3 4 5])
         (focus sut/next [1 2 3 4 5])))

  (is (= [100 2 3 4 5]
         (put sut/first 100 [1 2 3 4 5])))

  (is (= [1 10 11 12]
         (put sut/rest [10 11 12] [1 2 3 4 5])
         (put sut/next [10 11 12] [1 2 3 4 5])
         (put sut/rest '(10 11 12) [1 2 3 4 5])
         (put sut/next '(10 11 12) [1 2 3 4 5])))

  (is (= [1 10]
         (put sut/rest [10] [1 2])
         (put sut/rest [10] [1])
         (put sut/next [10] [1 2])
         (put sut/next [10] [1])))

  (is (= [1]
         (put sut/rest [1] [])))

  (is (= [1]
         (put sut/next [1] nil))))

(deftest atom-test
  (is (= [1 2 3 4]
         (focus sut/atom (atom [1 2 3 4]))))

  (is (= [1 2 3 4]
         (deref (put sut/atom [1 2 3 4] (atom [])))))

  (is (= [1 2 3]
         (deref (over sut/atom pop (atom [1 2 3 4])))))

  (let [a (atom [1 2 3 4])
        state {:foo a :bar 2}
        result (over [:foo sut/atom] pop state)]
    (is (= result {:foo a :bar 2}))
    (is (= [1 2 3] (deref (:foo state)))))

  ;; how to break atomic semantics with sut/atom
  ;; we'll only demo this in clj, the same happens in cljs if you have any async
  ;; but the demo is messier
  ;; you can uncomment this and run it repeatedly to see it sometimes return 111, sometimes return 110 and sometimes 11

  #?(:clj
     (comment 
     (let [a (atom {:alpha 10, :bravo 20})
           state {:foo a :bar 2}
           ;; use sut/atom in a compound lens but not as the last lens
           alpha-lens [:foo sut/atom :alpha]
           ;; kick off our business logic process
           our-process (future (over alpha-lens inc state))
           ;; another parallel process adding 100
           another-process (future (over alpha-lens #(+ % 100) state))
           ;; wait for our-process to complete
           _ @our-process
           ;; wait for another-process to complete
           _ @another-process]
       (let [alpha (-> state :foo deref :alpha)]
         ;; So alpha is 10, then take, add 1, take, add 100 in parallel
         ;; of course it equals ... 11? 110? 111?
         ;; don't use atom lenses this way
         (is (= 111 alpha))
         )))))
