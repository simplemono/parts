(ns parts.httpkit.server
  "HTTP Kit server integration with SSE support.

   When a request has Accept: text/event-stream, injects :ring/sse-send! into the
   world map and handles the headers-only respond pattern for SSE."
  (:require [org.httpkit.server :as httpkit]
            [parts.ring.dispatch :as dispatch]
            [clojure.string :as str]))

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
            sse? (some-> (get-in request [:headers "accept"])
                         (str/includes? "text/event-stream"))
            sse-send! (when sse?
                        (fn [{:keys [event data id]}]
                          (httpkit/send! ch
                                        {:body (str (when event (str "event: " event "\n"))
                                                    (when id (str "id: " id "\n"))
                                                    (when data (str "data: " data "\n"))
                                                    "\n")}
                                        false)))
            respond (fn [response]
                      (if (and sse?
                               (= "text/event-stream"
                                  (get-in response [:headers "Content-Type"])))
                        ;; SSE: send headers, keep channel open
                        (httpkit/send! ch (select-keys response [:status :headers]) false)
                        ;; Regular: send full response and close
                        (httpkit/send! ch response true)))
            raise (fn [exception]
                    (when-let [log (:log/log w)]
                      (log {:log/error :ring/uncaught-exception
                            :exception exception}))
                    (httpkit/send! ch
                                  {:status 500
                                   :headers {"Content-Type" "text/plain"}
                                   :body "internal server error"})
                    (httpkit/close ch))
            async-handler (dispatch/async-ring-handler
                           (cond-> w
                             sse-send! (assoc :ring/sse-send! sse-send!)))]
        (async-handler request respond raise)))
    :on-close
    (fn [_ch _status]
      ;; Channel closed - could invoke cleanup callbacks
      )}))

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
