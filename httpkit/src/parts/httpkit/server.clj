(ns parts.httpkit.server
  (:require [org.httpkit.server :as httpkit]
            [parts.ring.dispatch :as dispatch]))

(defn default-opts
  [w]
  {:port 8080
   :max-body (* 1024 1024 100) ;; 100mb
   :error-logger (fn [text ex]
                   ;; Logs all exceptions that have not been caught by any Ring
                   ;; handler or middleware. Httpkit responds with a HTTP 500
                   ;; with the exception message.
                   (when-let [log (:log/log w)]
                     (log {:log/error :ring/uncaught-exception
                           :exception ex
                           :message text})))})

(defn stop!
  [system-atom]
  (when-let [httpkit-stop* (:httpkit/stop! @system-atom)]
    (httpkit-stop*)))

(defn ring-handler
  [system-atom request]
  (httpkit/as-channel
   request
   {:on-open
    (fn [ch]
      (let [w @system-atom
            ring-async-handler (dispatch/async-ring-handler w)]
        (ring-async-handler
         request
         (fn [response]
           (httpkit/send! ch response))
         (fn [exception]
           (when-let [log (:log/log w)]
             (log {:log/error :ring/uncaught-exception
                   :exception exception}))
           (httpkit/send! ch
                          {:status 500
                           :headers {"Content-Type" "text/plain"}
                           :body "internal server error"})
           (httpkit/close ch)))))}))

(defn start!
  ([system-atom opts]
   (stop! system-atom)
   (let [httpkit-stop! (httpkit/run-server
                        (partial #'ring-handler
                                 system-atom)
                        opts)]
     (swap! system-atom
            assoc
            :httpkit/stop!
            httpkit-stop!)))
  ([system-atom]
   (start! system-atom
           (default-opts @system-atom))))
