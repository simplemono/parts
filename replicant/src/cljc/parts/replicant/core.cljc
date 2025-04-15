(ns parts.replicant.core
  "Some core parts for Replicant applications."
  (:require [clojure.walk :as walk]))

(defn enrich-action
  [w]
  (update
    w
    :action
    (fn [action]
      (walk/postwalk
        (fn [x]
          (some
            (fn [action-enricher]
              (action-enricher (assoc w
                                      :ui.action/x x)))
            (concat
              (:ui/action-enrichers w)
              [:ui.action/x]))
          )
        action))))

(defn get-action-handlers
  [w]
  (into {}
        (keep
          (fn [entry]
            (when-let [action-handler (:ui.action/handler entry)]
              [(:ui.action/kind entry)
               action-handler])))
        ((:ui/get-register w))))

(defn get-predicates
  [w]
  (into {}
        (keep
          (fn [entry]
            (when-let [predicate (:ui.predicate/fn entry)]
              [(:ui.predicate/kind entry)
               predicate])))
        ((:ui/get-register w))))

(defn get-action-enrichers
  [w]
  (into []
        (keep
          :ui.action-enricher/fn)
        ((:ui/get-register w))))

(defn event-handler
  [{:keys [ui/store ui/log] :as w
    :or {log identity}}]
  (let [action-handlers (get-action-handlers w)
        predicates (get-predicates w)
        action-enrichers (get-action-enrichers w)]
    (fn event-handler [replicant-data actions]
      (prn "predicatespredicatespredicates" predicates)
      (loop [actions actions]
        (when-let [action (first actions)]
          (let [params (-> (merge replicant-data
                                  w
                                  {:ui/event-handler event-handler
                                   :store store
                                   :action action
                                   :ui/action-enrichers action-enrichers})
                           (enrich-action))
                action-enriched (:action params)]
            (log {:log/level :debug
                  :log/message "Triggered action"
                  :ui/action action
                  :ui/action-enriched action-enriched})
            (if-let [predicate (predicates (first action))]
              (if (predicate params)
                ;; Only continue if predicate returns a truthy value:
                (recur (rest actions))
                (log {:log/level :debug
                      :log/message "Predicate stopped the process"
                      :ui/action action}))
              (if-let [handler (get action-handlers
                                    (first action))]
                (do (handler params)
                    (recur (rest actions)))
                (log {:log/level :warn
                      :log/message "Unknown action"
                      :ui/action action}))
              )))))))
