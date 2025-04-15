(ns parts.replicant.transit
  "Util to encode and decode transit data."
  (:require [cognitect.transit :as t]))

(defn transit-decode
  "Transit decode an object from `s`."
  ([s type opts]
   (let [rdr (t/reader type opts)]
     (.read rdr s)))
  ([s opts]
   (transit-decode s
                   :json
                   opts))
  ([s]
   (transit-decode s
                   {})))

(defn transit-encode
  ([input type opts]
   (let [writer (t/writer type opts)]
     (t/write writer input)))
  ([input opts]
   (transit-encode input
                   :json
                   opts))
  ([input]
   (transit-encode input
                   {})))

(comment
  (def _encoded
    (transit-encode
      [{:foo "bar"}
       {:baz "bat"}
       "lorem ipsum"
       123
       {:fiz {:lak "pom"}}]))

  (def _decoded
    (transit-decode _encoded))
  )
