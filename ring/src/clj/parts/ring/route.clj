(ns parts.ring.route
  "Allows to register routes to handle Ring requests.

   Compojure's [clout](https://github.com/weavejester/clout) is used for
   matching the `:uri` of the `:ring/request`.

   Use:

       {:ring/route [:get \"/article/:title\"]
        :ring/handler #'your-ring-handler}

   to register a route in the system. The ring-handler will receive a world map
   with entry `:ring/route-params` that will the route parameters which
   clout extracted from the `:uri`."
  (:require [clout.core :as clout]))

(defn router
  [{:keys [system/get-register ring/request] :as w}]
  (some
    (fn [{:keys [ring/route ring/handler]}]
      (let [[request-method clout-route] route]
        (when (= (:request-method request)
                 request-method)
          (when-let [route-params (clout/route-matches
                                    clout-route
                                    request)]
            (handler
              (assoc w
                     :ring/route-params route-params))))))
    (get-register)))

(def register
  [{:ring/dispatcher #'router}])
