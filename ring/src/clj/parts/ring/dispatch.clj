(ns parts.ring.dispatch)

(defn dispatch
  "Dispatches the world value with the `:ring/request` to the registered
   `:ring/dispatcher`s."
  [{:keys [system/get-register ring/respond] :as w}]
  (some
    (fn [entry]
      (when-let [dispatcher (:ring/dispatcher entry)]
        (when-let [w* (dispatcher w)]
          (when-let [response (:ring/response w*)]
            (respond response))
          w*)))
    (get-register)))

(defn async-ring-handler
  "The main async Ring handler of the system, which will receive all incoming
   requests and dispatch them to the registered `:ring/dispatcher`s."
  [w]
  (fn [request respond raise]
    (when-not (try
                (dispatch
                  (assoc w
                         :ring/request request
                         :ring/respond respond
                         :ring/raise raise))
                (catch Exception e
                  (raise e)))
      (respond (if-let [not-found-handler (:ring/not-found-handler w)]
                 (not-found-handler w)
                 {:status 404
                  :headers {"Content-Type" "text/plain"}
                  :body "not found"})))))
