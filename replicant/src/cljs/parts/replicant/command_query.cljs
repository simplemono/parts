(ns parts.replicant.command-query
  (:require [parts.replicant.command :as command]
            [parts.replicant.transit :as transit]
            [parts.replicant.query :as query]
            ))

(defn query-backend
  [{:keys [ui/store ui/event-store action :transit/read-opts :transit/write-opts] :as w}]
  (let [[_ query] action]
    (swap! event-store
           conj
           {:event/kind :query/request
            :query/status :query.status/loading
            :query/user-time (js/Date.)
            :query query})
    (-> (js/fetch (or (:ui/query-endpoint w)
                      "/query")
                  #js {:method "POST"
                       :body (transit/transit-encode query
                                                     write-opts)})
        (.then #(.text %))
        (.then (fn [text]
                 (transit/transit-decode text
                                         read-opts)))
        (.then (fn [response]
                 (swap! event-store
                        conj
                        (cond-> {:event/kind :query/response
                                 :query query
                                 :query/status (if (:success? response)
                                                 :query.status/success
                                                 :query.status/error)
                                 :query/user-time (js/Date.)}
                          (:success? response)
                          (assoc :query/result (:result response)))
                        )))
        (.catch (fn [error]
                  (js/console.error "query-backend error:" error)
                  (swap! store
                         query/receive-response
                         (js/Date.)
                         query
                         {:error (.-message error)}))))))

(defn issue-command
  [{:keys [ui/store action :transit/read-opts :transit/write-opts] :as w}]
  (let [[_ command & [{:keys [on-success on-error]}]] action
        event-handler (:ui/event-handler w)]
    (swap! store command/issue-command (js/Date.) command)
    (-> (js/fetch (or (:ui/command-endpoint w)
                      "/command")
                  #js {:method "POST"
                       :body (transit/transit-encode command
                                                     write-opts)})
        (.then #(.text %))
        (.then (fn [text]
                 (transit/transit-decode text
                                         read-opts)))
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

(defn query-response-reducer
  [{:keys [state event]}]
  (if (#{:query/request
         :query/response}
        (:event/kind event))
    (update-in state
               [:ui.query/log (:query event)]
               query/add-log-entry
               (dissoc event
                       :query
                       :event/kind))
    state))

(def register
  [{:ui.action/kind :data/query
    :ui.action/handler query-backend}

   {:ui.reducer/fn query-response-reducer}

   {:ui.action/kind :data/command
    :ui.action/handler issue-command}])
