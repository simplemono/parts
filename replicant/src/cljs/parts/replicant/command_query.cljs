(ns parts.replicant.command-query
  (:require [parts.replicant.command :as command]
            [parts.replicant.transit :as transit]
            [parts.replicant.query :as query]
            ))

(defn query-backend
  [{:keys [ui/store action :transit/read-opts :transit/write-opts] :as w}]
  (let [[_ query] action]
    (swap! store query/send-request (js/Date.) query)
    (-> (js/fetch (or (:ui/query-endpoint w)
                      "/query")
                  #js {:method "POST"
                       :body (transit/transit-encode query
                                                     write-opts)})
        (.then #(.text %))
        (.then (fn [text]
                 (transit/transit-decode text
                                         read-opts)))
        (.then #(swap! store query/receive-response (js/Date.) query %))
        (.catch (fn [error]
                  (js/console.error "query-backend error:" error)
                  (swap! store
                         query/receive-response
                         (js/Date.)
                         query
                         {:error (.-message error)}))))))

(defn ensure-command-uuid
  [command]
  (cond-> command
    (not (:command/uuid command))
    (assoc :command/uuid (random-uuid))))

(defn issue-command
  [{:keys [ui/store action :transit/read-opts :transit/write-opts] :as w}]
  (let [[_ command & [{:keys [on-success on-error]}]] action
        event-handler (:ui/event-handler w)
        command* (ensure-command-uuid command)]
    (swap! store command/issue-command (js/Date.) command)
    (-> (js/fetch (or (:ui/command-endpoint w)
                      "/command")
                  #js {:method "POST"
                       :body (transit/transit-encode command*)})
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
                                   ;; A command-fn returns the error under [:result :error].
                                   ;; When the command-fn throws, the server returns
                                   ;; {:error :command-fn-failed} at the top level.
                                   :error (or (get-in res
                                                    [:result
                                                     :error])
                                              (:error res))}
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
