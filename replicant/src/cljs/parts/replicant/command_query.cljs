(ns parts.replicant.command-query
  (:require [parts.replicant.command :as command]
            [parts.replicant.transit :as transit]
            [parts.replicant.query :as query]
            ))

(defn query-backend
  [{:keys [ui/store action] :as w}]
  (let [[_ query] action]
    (swap! store query/send-request (js/Date.) query)
    (-> (js/fetch (or (:ui/query-endpoint w)
                      "/query")
                  #js {:method "POST"
                       :body (transit/transit-encode query)})
        (.then #(.text %))
        (.then transit/transit-decode)
        (.then #(swap! store query/receive-response (js/Date.) query %))
        (.catch (fn [error]
                  (js/console.error "query-backend error:" error)
                  (swap! store
                         query/receive-response
                         (js/Date.)
                         query
                         {:error (.-message error)}))))))

(defn issue-command
  [{:keys [ui/store action] :as w}]
  (let [[_ command & [{:keys [on-success on-error]}]] action
        event-handler (:ui/event-handler w)]
    (swap! store command/issue-command (js/Date.) command)
    (-> (js/fetch (or (:ui/command-endpoint w)
                      "/command")
                  #js {:method "POST"
                       :body (transit/transit-encode command)})
        (.then #(.text %))
        (.then transit/transit-decode)
        (.then (fn [res]
                 (swap! store command/receive-response (js/Date.) command res)
                 (when (and (:success? res)
                            on-success)
                   (event-handler {}
                                  on-success))
                 (when (and (not (:success? res))
                            on-error)
                   (event-handler {:command command
                                   :response res
                                   :error (get-in res
                                                  [:result
                                                   :error])}
                                  on-error))))
        (.catch (fn [error]
                  (js/console.error "issue-command error:" error)
                  (swap! store
                         command/receive-response
                         (js/Date.)
                         command
                         {:error (.-message error)}))))))

(def register
  [{:ui.action/kind :data/query
    :ui.action/handler query-backend}

   {:ui.action/kind :data/command
    :ui.action/handler issue-command}])
