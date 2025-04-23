(ns parts.ring.dispatch
  "A `:ring/dispatcher` is responsible to find a Ring handler that handles the
   request.

   Each registered dispatcher receives the world value with the `:ring/request`.
   By returning a truthy value a dispatcher signals that it wants to handle the
   request. Usually it returns the world value with a new `:ring/response` entry
   that will be used as Ring response map. If a `:ring/response` entry is
   missing, then `dispatch` assumes that `:ring/request` will be handled
   asynchronously. For that reason `async-ring-handler` adds the `:ring/respond`
   and `:ring/raise` entries that correspond to the Ring functions of an async
   Ring handler.")

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
