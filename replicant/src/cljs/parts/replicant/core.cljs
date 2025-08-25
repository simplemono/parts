(ns parts.replicant.core
  "Some core parts for Replicant applications."
  (:require [clojure.walk :as walk]
            [replicant.dom :as r]
            [parts.replicant.router :as router]
            [replicant.alias :as alias]))

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
  [register]
  (into {}
        (keep
          (fn [entry]
            (when-let [action-handler (:ui.action/handler entry)]
              [(:ui.action/kind entry)
               action-handler])))
        register))

(defn get-predicates
  [register]
  (into {}
        (keep
          (fn [entry]
            (when-let [predicate (:ui.predicate/fn entry)]
              [(:ui.predicate/kind entry)
               predicate])))
        register))

(defn get-action-enrichers
  [register]
  (into []
        (keep
          :ui.action-enricher/fn)
        register))

(defn add-event-correlation
  [event]
  (update event
          :event/correlation
          (fn [uuid]
            (or uuid
                (random-uuid)))))

(defn dispatch-action!
  [w]
  (js/Promise.
    (fn [resolve reject]
      (let [action (:action w)]
        (if-let [handler (get (:ui/action-handlers w)
                              (first action))]
          (.then (js/Promise.resolve (handler w))
                 (fn [result]
                   (resolve result)))
          (reject {:error :unknown-action}))))))

(defn apply-reducers
  [state event reducers]
  (reduce
    (fn [state* entry]
      (if-let [reducer (:ui.reducer/fn entry)]
        (reducer {:state state*
                  :event event}
                 event)
        state*))
    state
    reducers))

(defn append-events
  [event-store-state new-events]
  (apply conj
         event-store-state
         new-events))

(defn apply-events
  [state new-events reducers]
  (reduce
    (fn [state* event]
      (apply-reducers state*
                      event
                      reducers))
    state
    new-events))

(defn dispatch-events!
  [w]
  (let [new-events (map
                     (fn [new-event]
                       (update new-event
                               :event/correlation
                               (fn [uuid]
                                 (or uuid
                                     (:event/correlation
                                      (add-event-correlation
                                        (:event w)))))))
                     (:new-events w))]
    (swap! (:ui/store w)
           apply-events
           new-events
           ((:ui/get-register w)))
    (swap! (:ui/event-store w)
           append-events
           new-events)))

(defn run-in-sequence [steps]
  (reduce
    (fn [p step]
      (.then p (fn [_] (step))))
    (js/Promise.resolve)  ;; initial resolved promise
    steps))

(defn event-handler
  [{:keys [ui/store ui/log] :as w
    :or {log identity}}]
  (let [get-action-handlers* (memoize get-action-handlers)
        get-predicates* (memoize get-predicates)
        get-action-enrichers* (memoize get-action-enrichers)]
    (fn event-handler [replicant-data actions]
      (run-in-sequence
        (map
          (fn [action]
            (fn []
              (js/Promise.
                (fn [resolve reject]
                  (let [register ((:ui/get-register w))
                        params (-> (merge replicant-data
                                          w
                                          {:ui/event-handler event-handler
                                           :store store
                                           :state @store
                                           :action action
                                           :ui/action-handlers (get-action-handlers* register)
                                           :ui/action-enrichers (get-action-enrichers* register)})
                                   (enrich-action))]
                    (if-let [predicate (get (get-predicates* register)
                                            (first action))]
                      (when-not (predicate params)
                        (log {:log/level :debug
                              :log/message "Predicate stopped the process"
                              :ui/action action})
                        (reject :predicate-reject))
                      ;; action:
                      (do
                        (log {:log/level :debug
                              :log/message "Triggered action"
                              :ui/action action})
                        (-> params
                            (dispatch-action!)
                            (.then (fn [action-result]
                                     (dispatch-events!
                                       (assoc w
                                              :new-events
                                              (:new-events action-result)))
                                     (resolve action-result)
                                     ;; event.reactions are applied async
                                     ))))))))))
          actions)))))

(defn add-ui-log
  [w]
  (assoc w
         :ui/log
         (fn [log-entry]
           ((or (aget js/console
                      (name (:log/level log-entry)))
                js/console.log)
            (pr-str log-entry)))))

(defn add-store
  [w]
  (assoc w
         :ui/store
         (atom {})))

(defn add-event-store
  [w]
  (assoc w
         :ui/event-store
         (atom [])))

(defn add-pages
  [w]
  (assoc w
         :ui/pages
         (filter
           (fn [entry]
             (and (:page-id entry)
                  (:render entry)))
           ((:ui/get-register w)))))

(defn add-routes
     [w]
     (assoc w
            :ui/routes
            (router/make-routes (:ui/pages w))))

(defn add-render-watcher!
  [w]
  (add-watch (:ui/store w)
             :ui/render-watcher
             (fn [_ _ _ state]
               ((:ui/render! w) (assoc w
                                       :ui/state
                                       state))))
  w)

(defn add-event-handler
  [w]
  (assoc w
         :ui/event-handler
         (event-handler w)))

(defn add-dispatch!
  [w]
  (r/set-dispatch! (:ui/event-handler w))
  w)

(defn add-store-dispatch!
  [w]
  (swap! (:ui/store w)
         assoc
         :replicant/dispatch!
         (fn [{:keys [actions] :as params}]
           ((:ui/event-handler w) (dissoc params
                                          :actions)
            actions)))
  w)

(defn add-route-click!
  [w]
  (js/document.body.addEventListener
    "click"
    (fn [event]
      (router/route-click (assoc w
                                 :event
                                 event))))
  w)

(defn add-navigate!
  [w]
  (js/window.addEventListener
    "popstate"
    (fn [_] (router/navigate! w)))
  w)

(defn add-routing-anchor!
  [w]
  (alias/register! :ui/a router/routing-anchor)
  w)

(defn event-reducer
  [w]
  (fn [state event]
    (apply-reducers state
                    event
                    ((:ui/get-register w)))))

(defn event-dispatch!
  [w]
  (doseq [entry (filter :event.reaction/kind
                        ((:ui/get-register w)))]
    (when (and (:event/kind (:event w))
               (= (:event.reaction/kind entry)
                  (:event/kind (:event w))))
      (.then (js/Promise.resolve
               ((:event.reaction/fn entry)
                w))
             (fn [result]
               (let [new-events (:new-events result)]
                 (when (seq new-events)
                   ((:ui/dispatch-events! w)
                    new-events))))))))

(defn event-store-dispatcher
  [w]
  (fn [_key _atom old-state new-state]
    (doseq [new-event (drop (count old-state)
                            new-state)]
      (event-dispatch! (assoc w
                              :event
                              new-event))))
  )

(defn add-event-store-dispatcher
  [w]
  (add-watch (:ui/event-store w)
             :event-store-dispatcher
             (event-store-dispatcher w))
  w)

(defn add-dispatch-events
  [w]
  (assoc w
         :ui/dispatch-events!
         (fn [new-events]
           (dispatch-events! (assoc w
                                    :new-events
                                    new-events)))))
