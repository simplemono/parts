(ns parts.routine.core-test
  (:require [clojure.test :as t :refer [is are deftest testing]]
            [parts.routine.core :as routine]))

(deftest initial-delay
  (testing "should allow to define the initial delay of the routine via `:routine/initial-delay-ms`"
    (let [counter (atom 0)
          w (routine/start!
              {:system/get-register (fn []
                                      [{:routine/category :light
                                        :routine/fn (fn []
                                                      (swap! counter inc))
                                        :routine/initial-delay-ms 0
                                        :routine/interval-ms 100}])})]
      (Thread/sleep 90)
      (routine/stop! w)
      (is (= @counter 1)))))

(deftest first-test
  (is (= 1 1)))

;; (t/run-tests)
