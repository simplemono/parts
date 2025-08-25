(ns parts.replicant.command-query
  (:require [parts.replicant.command :as command]
            [parts.replicant.transit :as transit]
            [parts.replicant.query :as query]
            ))

(defn query-backend
  [{:keys [ui/dispatch-events! action :transit/read-opts :transit/write-opts] :as w}]
  (let [[_ query] action]
    (dispatch-events! [{:event/kind :query/request
                        :query/status :query.status/loading
                        :query/user-time (js/Date.)
                        :query query}])
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
                 (dispatch-events!
                   [(cond-> {:event/kind :query/response
                             :query query
                             :query/status (if (:success? response)
                                             :query.status/success
                                             :query.status/error)
                             :query/user-time (js/Date.)}
                      (:success? response)
                      (assoc :query/result (:result response)))]
                   )))
        (.catch (fn [error]
                  (js/console.error "query-backend error:" error)
                  (dispatch-events!
                    [{:event/kind :query/error
                      :query/status :query.status/error
                      :query/user-time (js/Date.)
                      :error (.-message error)}]))))))

(defn issue-command
  [{:keys [ui/dispatch-events! action :transit/read-opts :transit/write-opts] :as w}]
  (let [[_ command & [{:keys [on-success on-error]}]] action
        event-handler (:ui/event-handler w)]
    (dispatch-events!
      [{:event/kind :command/request
        :command/status :command.status/issued
        :command/user-time (js/Date.)
        :command command}])
    (-> (js/fetch (or (:ui/command-endpoint w)
                      "/command")
                  #js {:method "POST"
                       :body (transit/transit-encode command
                                                     write-opts)})
        (.then #(.text %))
        (.then (fn [text]
                 (transit/transit-decode text
                                         read-opts)))
        (.then (fn [response]
                 (dispatch-events!
                   [(cond-> {:event/kind :command/response
                             :command command
                             :command/status (if (:success? response)
                                               :command.status/success
                                               :command.status/error)
                             :command/user-time (js/Date.)}
                      (:result response)
                      (assoc :command/result (:result response)))])
                 (when (and (:success? response)
                            on-success)
                   (event-handler {}
                                  on-success))
                 (when (and (not (:success? response))
                            on-error)
                   (event-handler {:command command
                                   :response response
                                   :error (get-in response
                                                  [:result
                                                   :error])}
                                  on-error))
                 (:result response)))
        (.catch (fn [error]
                  (js/console.error "issue-command error:" error)
                  (dispatch-events!
                    [{:event/kind :command/error
                      :command/status :command.status/error
                      :command/user-time (js/Date.)
                      :error (.-message error)}]))))))

(defn query-reducer
  [{:keys [state event]}]
  (if (#{:query/request
         :query/response
         :query/error}
        (:event/kind event))
    (update-in state
               [:ui.query/log (:query event)]
               query/add-log-entry
               (dissoc event
                       :query))
    state))

(defn command-reducer
  [{:keys [state event]}]
  (if (#{:command/request
         :command/response
         :command/error}
        (:event/kind event))
    (update-in state
               [:ui.command/log (:command event)]
               command/add-log-entry
               (dissoc event
                       :command))
    state))

(def register
  [{:ui.action/kind :data/query
    :ui.action/handler query-backend}

   {:ui.reducer/fn query-reducer}
   {:ui.reducer/fn command-reducer}

   {:ui.action/kind :data/command
    :ui.action/handler issue-command}])
