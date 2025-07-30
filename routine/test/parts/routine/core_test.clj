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

(deftest light-routine-should-never-run-in-parallel
  (testing "light routine should never run in parallel"
    (let [counter (atom 0)
          w (routine/start!
              {::routine/force-termination-timeout 0
               ::routine/termination-timeout 0
               :system/get-register (fn []
                                      [{:routine/category :light
                                        :routine/fn (fn []
                                                      (swap! counter inc)
                                                      (Thread/sleep 1000)
                                                      )
                                        :routine/initial-delay-ms 0
                                        :routine/interval-ms 10}])})]
      (Thread/sleep 100)
      (routine/stop! w)
      (is
        (= @counter 1))
      ))
  )

(deftest heavy-routine-should-never-run-in-parallel
  (testing "heavy routine should never run in parallel"
    (let [counter (atom 0)
          w (routine/start!
              {::routine/force-termination-timeout 0
               ::routine/termination-timeout 0
               :system/get-register (fn []
                                      [{:routine/category :heavy
                                        :routine/fn (fn []
                                                      (swap! counter inc)
                                                      (Thread/sleep 1000)
                                                      )
                                        :routine/initial-delay-ms 0
                                        :routine/interval-ms 10}])})]
      (Thread/sleep 100)
      (routine/stop! w)
      (is
        (= @counter 1))
      ))
  )

(deftest no-more-than-x-heavy-routines
  (testing "no more than x heavy routines"
    (let [counter (atom 0)
          w (routine/start!
              {::routine/force-termination-timeout 0
               ::routine/termination-timeout 0
               ::routine/heavy-routine-pool-size 2
               :system/get-register (fn [] (repeatedly 10
                                                       (fn []
                                                         {:routine/category :heavy
                                                          :routine/fn (fn []
                                                                        (swap! counter inc)
                                                                        (Thread/sleep 1000)
                                                                        )
                                                          :routine/initial-delay-ms 0
                                                          :routine/interval-ms 10})))})]
      (Thread/sleep 100)
      (routine/stop! w)
      (is
        (= @counter 2))
      )))

(deftest no-more-than-x-light-routines
  (testing "no more than x light routines"
    (let [counter (atom 0)
          w (routine/start!
              {::routine/force-termination-timeout 0
               ::routine/termination-timeout 0
               ::routine/light-routine-pool-size 10
               :system/get-register (fn [] (repeatedly 10
                                                       (fn []
                                                         {:routine/category :light
                                                          :routine/fn (fn []
                                                                        (swap! counter inc)
                                                                        (Thread/sleep 1000)
                                                                        )
                                                          :routine/initial-delay-ms 0
                                                          :routine/interval-ms 10})))})]
      (Thread/sleep 100)
      (routine/stop! w)
      (is
        (= @counter 10))
      )))
