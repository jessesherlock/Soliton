(ns soliton.sm.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [soliton.lens :as lens]
            [soliton.core]
            [soliton.sm.core :as sut]))

(deftest single-test
  (is (= 42 (sut/focus :foo {:foo 42})))
  (is (= {:foo 42} (sut/put :foo 42 {:foo nil})))
  (is (= {:foo 43} (sut/over :foo inc {:foo 42}))))

(deftest vector-lens-test
  (is (= :value
         (sut/focus [:foo 1] {:foo [:x :value]})))
  
  (is (= {:foo [:x :value :y]}
         (sut/put [:foo 1] :value {:foo [:x :x :y]})))
  
  (is (= {:foo [:x 43 :y]}
         (sut/over [:foo 1] inc {:foo [:x 42 :y]})))
  
  (is (= {:foo {:bar :x}}
         (sut/put [:foo :bar] :x {})))

  (is (= {:foo [nil :x]}
         (sut/put [:foo 1] :x {})))

  ;; Have covered subvecs too?
  (is (= :value 
         (sut/focus (subvec [:foo 1 2] 0 2) {:foo [:x :value]}))))

(deftest map-compound-lens-test
  (let [test-map {:eu {:italy {:capital :rome, :lang :italian}
                       :france {:capital :paris, :lang :french}}
                  :australia {:capital :canberra, :lang :english}}]

    (is (= {:a :rome, :b :english}
           (sut/focus {:a [:eu :italy :capital]
                       :b [:australia :lang]}
                      test-map)))

    (is (= {:eu {:italy :replaced-italy
                 :france {:capital :paris :lang :french}}
            :australia :replaced-australia}
           (sut/put {:a [:eu :italy]
                     :b :australia}
                    {:a :replaced-italy
                     :b :replaced-australia}
                    test-map)))
    
    (is (= {:eu {:italy {:capital :rome, :lang :italian, :i-was-A true}
                 :france {:capital :paris, :lang :french}}
            :australia {:capital :canberra, :lang :english, :i-was-B true}}
           (sut/over {:a [:eu :italy]
                      :b :australia}
                     (fn [{:keys [a b]}]
                       {:a (assoc a :i-was-A true)
                        :b (assoc b :i-was-B true)})
                     test-map)
           (sut/put {:a [:eu :italy lens/merge]
                     :b [:australia lens/merge]}
                    {:a {:i-was-A true}
                     :b {:i-was-B true}}
                    test-map)))

    (is (= {:eu {:italy {:capital :rome :lang ":italian"}
                 :france {:capital :paris :lang 'french}}
            :australia {:capital :canberra :lang "english"}}
           (sut/over {str [:eu :italy :lang]
                      symbol [:eu :france :lang]
                      #(str (symbol %)) [:australia :lang]}
                     (fn [m] (into {} (map (fn [[k v]] [k (k v)])) m))
                     test-map)))))

(deftest sets-compound-lens-test
  (let [test-map {:eu {:italy {:capital :rome, :lang :italian}
                       :france {:capital :paris, :lang :french}}
                  :australia {:capital :canberra, :lang :english}}]

    (is (= #{:rome :canberra}
           (sut/focus #{[:eu :italy :capital]
                        [:australia :capital]}
                      test-map)))

    (is (= #{:italian, :french}
           (sut/focus [:eu #{[:italy :lang]
                             [:france :lang]}]
                      test-map)))
    (is (= {:eu {:italy {:capital :rome, :lang :italian, :tagged true}
                 :france {:capital :paris, :lang :french, :tagged true}}
            :australia {:capital :canberra, :lang :english}}
           (sut/put [:eu #{[:italy :tagged]
                           [:france :tagged]}]
                    true
                    test-map)))

    (is (= {:eu {:italy {:capital "rome", :lang :italian}
                 :france {:capital "paris", :lang :french}}
            :australia {:capital :canberra :lang :english}}
           (sut/over [:eu #{[:italy :capital]
                            [:france :capital]}]
                     name
                     test-map)))))

(deftest list-compound-lens-test
  (is (= (list :rome :paris)
         (sut/focus [:eu (list [:italy :capital] [:france :capital])]
                    {:eu {:italy {:capital :rome, :lang :italian}
                          :france {:capital :paris, :lang :french}}
                     :australia {:capital :canberra, :lang :english}})))
  (is (= {:a :alpha
          :b :bravo}
         (sut/put '(:a :b) '(:alpha :bravo) {:a nil :b nil})))
  (is (= {:nums {:one 1 :two 2 :three 3}}
         (sut/over [:nums (list :one :two :three)]
                   (fn [args] (map inc args))
                   {:nums {:one 0 :two 1 :three 2}}))))

(deftest focus-steps-test
  (is (= [{:lenses [:foo 1], :state {:foo [:x :value]}}
          {:lenses [1], :state [:x :value]}
          {:state :value}]
         (sut/focus-steps [:foo 1] {:foo [:x :value]}))))

(deftest put-steps-test
  (is (= [{:lenses [:foo 1]
           :state {:foo [:x :x :y]}
           :stack [], :operand :value}
          {:lenses [1]
           :state [:x :x :y]
           :stack [[:foo {:foo [:x :x :y]}]]
           :operand :value}
          {:state [:x :value :y]
           :stack [[:foo {:foo [:x :x :y]}]]}
          {:state {:foo [:x :value :y]}
           :stack []}]
         (sut/put-steps [:foo 1] :value {:foo [:x :x :y]}))))

(deftest over-steps-test
  (is (= [{:lenses [:foo 1]
           :state {:foo [:x 42 :y]}
           :stack []
           :operand inc}
          {:lenses [1]
           :state [:x 42 :y]
           :stack [[:foo {:foo [:x 42 :y]}]]
           :operand inc}
          {:state [:x 43 :y]
           :stack [[:foo {:foo [:x 42 :y]}]]}
          {:state {:foo [:x 43 :y]}
           :stack []}]
         (sut/over-steps [:foo 1] inc {:foo [:x 42 :y]}))))

(deftest reflect-test
  (let [test-map {:bravo 1
                  :alpha {:bravo 2
                          :charlie 1}}]
    (testing "3 argument reflection, add :bravo to [:alpha :bravo] and put result in :bravos"
      (is (= {:bravo 1
              :alpha {:bravo 2
                      :charlie 1}
              :bravos 3}

             (sut/over (soliton.core/reflector :bravo [:alpha :bravo] :bravos) + test-map)

             (sut/reflect [:bravo [:alpha :bravo] :bravos] + test-map)

             ((sut/<> + :bravo [:alpha :bravo] :bravos) test-map))))

    (testing "2 argument reflection, arg is [:alpha :charlie] and result in :charlie-incd"
        (is (= {:bravo 1
                :alpha {:bravo 2
                        :charlie 1}
                :charlie-incd 2}

               (sut/over (soliton.core/reflector [:alpha :charlie] :charlie-incd) inc test-map)

               (sut/reflect [[:alpha :charlie] :charlie-incd] inc test-map)

               ((sut/<> inc [:alpha :charlie] :charlie-incd) test-map))))

    (testing "1 argument reflection, a normal non-reflection"
      (is (= {:bravo "1"
              :alpha {:bravo 2
                      :charlie 1}}

             (sut/over (soliton.core/reflector :bravo) str test-map)

             (sut/reflect [:bravo] str test-map)
           
             ((sut/<> str :bravo) test-map))))))


(deftest -<>-test
  (let [test-map {:bravo 1
                  :alpha {:bravo 2
                          :charlie 1}}]
       (is (= {:bravo 1
               :alpha {:bravo 2
                       :charlie 1}
               :bravos 3}

              {:bravo 1
               :alpha {:bravo 2
                       :charlie 1}
               :bravos 3}

              ((sut/<> + :bravo [:alpha :bravo] :bravos) test-map)

              (->> test-map ((sut/<> + :bravo [:alpha :bravo] :bravos)))

              (sut/-<> test-map (+ :bravo [:alpha :bravo] :bravos))))

       (is (= {:bravo 1
               :alpha {:bravo 2
                       :charlie 1}
               :bravos 3
               :total 7
               :total-str "7"
               :total-plus-10 17}

              {:bravo 1
               :alpha {:bravo 2
                       :charlie 1}
               :bravos 3
               :total 7
               :total-str "7"
               :total-plus-10 17}

              (sut/-<> test-map
                       (+ :bravo [:alpha :bravo] :bravos)
                       (+ :bravo [:alpha :bravo] [:alpha :charlie] :bravos :total)
                       (+ :total (lens/const 10) :total-plus-10)
                       (str :total :total-str))))))
