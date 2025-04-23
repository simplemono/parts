(ns parts.ring.transit
  "Parts to handle Ring requests with a transit+json payload."
  (:require [cognitect.transit :as transit]))

(defn transit-read-string
  "Reads transit JSON data from the `transit-string` and returns EDN."
  [transit-string]
  (let [in (java.io.ByteArrayInputStream.
             (.getBytes transit-string
                        "UTF-8"))
        reader (transit/reader in :json)]
    (transit/read reader)))

(defn transit-generate-string
  "Generates a transit JSON string from the `edn-data`."
  [edn-data]
  (let [out (java.io.ByteArrayOutputStream.)
        writer (transit/writer out
                               :json-verbose)]
    (transit/write writer
                   edn-data)
    (String. (.toByteArray out)
             "UTF-8")))

(defn transit-content?
  "Checks if the 'Content-Type' of the `ring-request` is
   'application/transit+json'."
  [ring-request]
  (= (get-in ring-request
             [:headers "content-type"])
     "application/transit+json"))

(defn unparsable-transit-json-response
  "Returns a Ring response map that indicates a bad request due to an
   unparsable transit+json body."
  []
  {:status 400
   :headers {"Content-Type" "application/transit+json"}
   :body (transit-generate-string
           {:error :unparsable-transit-json})})

(defn parse-transit
  "Tries to parse the 'transit+json' data in the `ring-request`
   `:body`. If the parsing fails throws an ex-info with an appropriate
   `:ring/response`."
  [ring-request]
  (try
    (let [body (:body ring-request)
          body-str (if (string? body)
                     body
                     (slurp body))]
      (transit-read-string body-str))
    (catch Exception e
      (throw (ex-info "failed to parse transit+json"
                      {:ring/response (unparsable-transit-json-response)}
                      e)))))

(defn transit-response
  "Returns a Ring HTTP 200 response map that contains the `edn-data`
   encoded as 'transit+json'."
  ([status edn-data]
   {:status status
    :headers {"Content-Type" "application/transit+json"}
    :body (transit-generate-string edn-data)})
  ([edn-data]
   (transit-response 200
                     edn-data)))

(defn add-request-params
  "Parses the `:body` of the `:ring/request` as `transit+json` and adds it as
   `:ring/request-params` to `w`."
  [w]
  (assoc w
         :ring/request-params
         (parse-transit (:ring/request w))))

(defn add-response
  "Adds a Ring HTTP 200 response map as `:ring/response` that contains the
   `:ring/response-params` encoded as 'transit+json'."
  [{:keys [ring/response-params] :as w}]
  (assoc w
         :ring/response
         (transit-response response-params)))
