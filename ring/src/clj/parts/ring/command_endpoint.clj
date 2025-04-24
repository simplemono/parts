(ns parts.ring.command-endpoint
  "The command HTTP POST endpoint is used by the client to send commands to the
   backend. A command is the data required to perform a side-effect. A request
   payload look like this:

       {:command/kind :command/team-add-user
        :command/data {:email \"joe@example.com\"}}

   Use the system register to register command handlers. Example:

       {:command/kind :command/team-add-user
        :command/fn #'command!}

   The `:command/fn` gets passed a world-value with the `:command` as entry (the
   payload of the request body). It should add a `:command/result` entry to the
   world-value, which should contain a `:success?` entry and optionally a
   `:result` entry. Example:

       {:success? false
        :result {:error \"Invalid email address\"}}

   Read more about the general concept here:

   https://replicant.fun/tutorials/network-writes/

   Surround `prepare` with your Ring handler and authentication parts. Write
   your own `prepare` function if you want to use something else than
   transit+json to encode the request and response params. The `:command/fn` is
   responsible to handle the authorization."
  (:require [parts.ring.transit :as transit])
  )

(defn find-command-def
  [register command]
  (when-let [kind (:command/kind command)]
    (some
      (fn [entry]
        (when (and (:command/fn entry)
                   (= (:command/kind entry)
                      kind))
          entry))
      register)))

(defn add-command-def
  [w]
  (assoc w
         :command-def
         (find-command-def ((:system/get-register w))
                           (:ring/request-params w))))

(defn add-response-params
  [w]
  (assoc w
         :ring/response-params
         (if-let [f (:command/fn (:command-def w))]
           (try
             (merge
               {:success? true}
               (-> w
                   (assoc :command (:ring/request-params w))
                   (f)
                   (:command/result)))
             (catch Exception e
               ((:log/log w
                          identity)
                {:log/error :command-fn-failed
                 :command (:ring/request-params w)
                 :exception e})
               {:error :command-fn-failed}))
           {:error :unkown-command-type
            :command (:ring/request-params w)})))

(defn prepare
  [w]
  (-> w
      (transit/add-request-params)
      (add-command-def)
      (add-response-params)
      (transit/add-response)))
