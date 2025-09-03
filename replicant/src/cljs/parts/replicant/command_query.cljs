(ns parts.replicant.command-query
  (:require [parts.replicant.command :as command]
            [parts.replicant.transit :as transit]
            [parts.replicant.query :as query]
            ))

(defn query-backend
  [{:keys [ui/event-handler action :transit/read-opts :transit/write-opts] :as w}]
  (let [[_ query] action]
    (event-handler {}
                   [[:query/send-request
                     (js/Date.)
                     query]])
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
                 (event-handler {}
                                [[:query/receive-response
                                  (js/Date.)
                                  query
                                  response]])
                 ))
        (.catch (fn [error]
                  (js/console.error "query-backend error:" error)
                  (event-handler {}
                                 [[:query/receive-response
                                   (js/Date.)
                                   query
                                   {:error (.-message error)}]]))))))

(defn issue-command
  [{:keys [ui/event-handler action :transit/read-opts :transit/write-opts] :as w}]
  (let [[_ command & [{:keys [on-success on-error]}]] action
        event-handler (:ui/event-handler w)]
    (event-handler {}
                   [[:command/issue-command
                     (js/Date.)
                     command]])
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
                 (event-handler {}
                                [[:command/receive-response
                                  (js/Date.)
                                  command
                                  res]])
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
                  (event-handler {}
                                 [[:command/receive-response
                                   (js/Date.)
                                   command
                                   {:error (.-message error)}]]))))))

(def register
  [{:ui.action/kind :data/query
    :ui.action/handler query-backend}

   {:ui.action/kind :query/send-request
    :ui.action/replay true
    :ui.action/handler (fn [{:keys [ui/state action]}]
                         {:ui/new-state (apply query/send-request
                                               state
                                               (rest action))})}

   {:ui.action/kind :query/receive-response
    :ui.action/replay true
    :ui.action/handler (fn [{:keys [ui/state action]}]
                         {:ui/new-state (apply query/receive-response
                                               state
                                               (rest action))})}

   {:ui.action/kind :command/issue-command
    :ui.action/replay true
    :ui.action/handler (fn [{:keys [ui/state action]}]
                         {:ui/new-state (apply command/issue-command
                                               state
                                               (rest action))})}

   {:ui.action/kind :command/receive-response
    :ui.action/replay true
    :ui.action/handler (fn [{:keys [ui/state action]}]
                         {:ui/new-state (apply state
                                               command/receive-response
                                               (rest action))})}

   {:ui.action/kind :data/command
    :ui.action/handler issue-command}])
