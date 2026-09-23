(ns parts.replicant.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [parts.replicant.core :as core]))

(defn checked-enricher
  "Stands in for the enricher of `:event/target.checked`: it answers
   with the checkbox state, which is `false` for an unchecked box."
  [checked?]
  (fn [{:keys [ui.action/x]}]
    (when (= x :event/target.checked)
      checked?)))

(deftest enrich-action-test
  (testing "a placeholder is replaced, everything else stays as it is"
    (is (= {:action [:store/assoc-in [:agreed?] true {:n 0 :s "" :k nil}]}
           (select-keys (core/enrich-action
                          {:ui/action-enrichers [(checked-enricher true)]
                           :action [:store/assoc-in [:agreed?] :event/target.checked {:n 0 :s "" :k nil}]})
                        [:action]))))
  (testing "false survives, both as an enricher's answer and inside the action data"
    (is (= {:action [:store/assoc-in [:agreed?] false {:flag false}]}
           (select-keys (core/enrich-action
                          {:ui/action-enrichers [(checked-enricher false)]
                           :action [:store/assoc-in [:agreed?] :event/target.checked {:flag false}]})
                        [:action]))))
  (testing "without enrichers the action is untouched"
    (is (= {:action [:a false nil 0]}
           (select-keys (core/enrich-action {:ui/action-enrichers []
                                             :action [:a false nil 0]})
                        [:action])))))
