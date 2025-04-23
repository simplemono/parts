(ns parts.replicant.basic
  "Some basic parts for Replicant apps.")

(def action-enrichers
  [{:ui.action-enricher/kind :event/target.value
    :ui.action-enricher/fn (fn [{:keys [ui.action/x] :as w}] ;; ui/action
                             (when (and (keyword? x)
                                        (= x
                                           :event/target.value))
                               (-> (:replicant/js-event w)
                                   .-target
                                   .-value)
                               ))}

   {:ui.action-enricher/kind :event/target.value-int
    :ui.action-enricher/fn (fn [{:keys [ui.action/x] :as w}]
                             ;; Same as `:event/target.value` but converts the
                             ;; String into a JS integer:
                             (when (and (keyword? x)
                                        (= x
                                           :event/target.value-int))
                               (-> (:replicant/js-event w)
                                   .-target
                                   .-value
                                   (js/parseInt))
                               ))}

   {:ui.action-enricher/kind :dom/node
    :ui.action-enricher/fn (fn [{:keys [ui.action/x] :as w}]
                             (when (and (keyword? x)
                                        (= x
                                           :dom/node))
                               (:replicant/node w)
                               ))}

   {:ui.action-enricher/kind :store/get
    :ui.action-enricher/fn (fn [{:keys [ui.action/x] :as w}]
                             (when(and (vector? x)
                                      (= :store/get (first x)))
                               (get (:ui/state w)
                                    (second x))))}
   ])

(def dom-action-handlers
  [{:ui.action/kind :dom/prevent-default
    :ui.action/handler (fn [params]
                         (.preventDefault ^js (get-in params
                                                      [:replicant-data
                                                       :js-event])))}
   {:ui.action/kind :dom/set-input-text
    :ui.action/handler (fn [{:keys [action]}]
                         (let [[_ & args] action]
                           (set! (.-value (first args)) (second args))))}
   {:ui.action/kind :dom/focus-element
    :ui.action/handler (fn [{:keys [action]}]
                         (let [[_ & args] action]
                           (.focus (first args))))}
   {:ui.action/kind :dom/alert
    :ui.action/handler (fn [{:keys [action]}]
                         (let [[_ & args] action]
                           (js/alert (first args))))}])

(def state-action-handlers
  [{:ui.action/kind :store/assoc
    :ui.action/handler (fn [{:keys [action store]}]
                         (apply swap! store assoc (rest action)))}
   {:ui.action/kind :store/assoc-in
    :ui.action/handler (fn [{:keys [action store]}]
                         (apply swap! store assoc-in (rest action)))}
   {:ui.action/kind :store/dissoc
    :ui.action/handler (fn [{:keys [action store]}]
                         (apply swap! store dissoc (rest action)))}])

(def predicates
  [
   {:ui.predicate/kind :js/on-enter
    :ui.predicate/fn (fn [{:keys [replicant/js-event]}]
                       (= (.-key js-event)
                          "Enter"))}

   {:ui.predicate/kind :js/confirm
    :ui.predicate/fn (fn [{:keys [action]}]
                       (js/confirm (second action)))}

   {:ui.predicate/kind :js/alert
    :ui.predicate/fn (fn [{:keys [action]}]
                       (js/alert (second action))
                       true)}

   {:ui.predicate/kind :js/location
    :ui.predicate/fn (fn [{:keys [action]}]
                       (js/location.assign (second action)))}

   {:ui.predicate/kind :store/true?
    :ui.predicate/fn (fn [{:keys [action store]}]
                       (let [key (second action)]
                         (true? (get @store
                                     key))))}
   ])

(defn get-all
  []
  (concat action-enrichers
          dom-action-handlers
          state-action-handlers
          predicates))
