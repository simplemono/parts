(ns parts.workos.callback
  (:require [parts.workos.core :as workos]
            [ring.middleware.params :as params]
            [clojure.edn :as edn]))

(defn add-authenticate-request
  [w]
  (assoc w
         :authenticate-request
         {:request-method :post
          :url "https://api.workos.com/user_management/authenticate"
          :form-params {:client_id (:workos/client-id w)
                        :client_secret ((:workos/get-api-key w))
                        :grant_type "authorization_code"
                        :code (get-in w
                                      [:ring/request
                                       :query-params
                                       "code"])
                        :ip_address (:remote-addr (:ring/request w))
                        :user_agent (get-in w
                                            [:ring/request
                                             :headers
                                             "user-agent"])}
          :content-type :json
          :as :json
          :throw-exceptions false}))

(defn add-authenticate-response
  [w]
  (assoc w
         :authenticate-response
         ((:workos/client w) (:authenticate-request w))))

(defn get-access-token
  [w]
  (get-in w
          [:authenticate-response
           :body
           :access_token]))

(defn add-access-token
  [w]
  (assoc w
         :auth/access-token
         (get-access-token w)))

(defn get-refresh-token
  [w]
  (get-in w
          [:authenticate-response
           :body
           :refresh_token]))

(defn add-refresh-token
  [w]
  (assoc w
         :auth/refresh-token
         (get-refresh-token w)))


(defn add-state-param
  [w]
  (assoc w
         :state-param
         (some-> w
                 (get-in [:ring/request
                          :query-params
                          "state"])
                 (edn/read-string))))

(defn prepare-auth
  [w]
  (-> w
      ;; (client/add-workos-client)
      (update :ring/request
              params/params-request)
      (workos/add-client-id)
      (workos/add-get-api-key)
      (add-authenticate-request)
      (add-authenticate-response)
      (add-access-token)
      (add-refresh-token)
      (workos/add-jwk-public-key)
      (workos/add-jwt)
      (add-state-param)))
