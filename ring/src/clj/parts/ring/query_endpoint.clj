(ns parts.ring.query-endpoint
  "The query HTTP post endpoint is used by the frontend to send queries to the
   backend. Each query is expected to be
   side-effect-free (https://de.wikipedia.org/wiki/Command-Query-Responsibility-Segregation).

   The request body and response is encoded as transit/json+verbose. A request body example:

       {:query/kind :query/team
        :query/data {...}}

   Use the system register to register new query implementations. Example:

       {:query/kind :query/team
        :query/fn #'prepare})

   The `:query/fn` receives a world-value with a `:query` entry (the request
   body as Clojure data). It is expected to add a `:query/result` and optionally
   a `:query/success?` entry (default value `true`) to the world value. So that a
   response body payload looks like this:

       {:success? true
        :result {:users [...]}}

   Read more about the general concept here:

   https://replicant.fun/tutorials/network-reads/

   Surround `prepare` with your Ring handler and authentication parts. Write
   your own `prepare` function if you want to use something else than
   transit+json to encode the request and response params. The `:query/fn` is
   responsible to handle the authorization."
  (:require [parts.ring.transit :as transit]))

(defn find-query-def
  [register query]
  (when-let [kind (:query/kind query)]
    (some
      (fn [entry]
        (when (and (:query/fn entry)
                   (= (:query/kind entry)
                      kind))
          entry))
      register)))

(defn add-query-def
  [w]
  (assoc w
         :query-def
         (find-query-def ((:system/get-register w))
                         (:ring/request-params w))))

(defn add-response-params
  [w]
  (assoc w
         :ring/response-params
         (if-let [f (:query/fn (:query-def w))]
           (try
             (let [w* (-> w
                          (assoc :query (:ring/request-params w))
                          (f))]
               {:success? (or (:query/success? w*)
                              true)
                :result (:query/result w*)})
             (catch Exception e
               ((:log/log w
                          identity)
                {:log/error :query-fn-failed
                 :exception e})
               {:success? false
                :error :query-fn-failed}))
           {:success? false
            :error :unkown-query-type
            :query (:ring/request-params w)})))

(defn prepare
  [w]
  (-> w
      (transit/add-request-params)
      (add-query-def)
      (add-response-params)
      (transit/add-response)))
