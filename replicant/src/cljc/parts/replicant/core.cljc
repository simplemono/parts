(ns parts.replicant.core
  "Some core parts for Replicant applications."
  (:require [clojure.walk :as walk]
            [replicant.dom :as r]

            #?@(:cljs
                [[parts.replicant.router :as router]
                 [replicant.alias :as alias]])))

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

(defn add-ui-log
  [w]
  (assoc w
         :ui/log
         (fn [log-entry]
           #?(:cljs
              ((or (aget js/console
                         (name (:log/level log-entry)))
                   js/console.log)
               (pr-str log-entry))))))

(defn add-store
  [w]
  (assoc w
         :ui/store
         (atom {})))

(defn add-pages
  [w]
  (assoc w
         :ui/pages
         (filter
           (fn [entry]
             (and (:page-id entry)
                  (:render entry)))
           ((:ui/get-register w)))))

#?(:cljs
   (defn add-routes
     [w]
     (assoc w
            :ui/routes
            (router/make-routes (:ui/pages w)))))

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

#?(:cljs
   (defn add-route-click!
     [w]
     (js/document.body.addEventListener
       "click"
       (fn [event]
         (router/route-click (assoc w
                                    :event
                                    event))))
     w))

#?(:cljs
   (defn add-navigate!
     [w]
     (js/window.addEventListener
       "popstate"
       (fn [_] (router/navigate! w)))
     w))

#?(:cljs
   (defn add-routing-anchor!
     [w]
     (alias/register! :ui/a router/routing-anchor)
     w))
