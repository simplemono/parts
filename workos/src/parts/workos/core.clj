(ns parts.workos.core
  (:require [clj-http.client :as http]
            [buddy.sign.jwt :as jwt]
            [buddy.core.keys.jwk.proto :as buddy-jwk]
            [lambdaisland.uri :as uri]
            [clojure.string :as str]
            ))

(defn workos-request
  "HTTP client for the [WorkOS API](https://workos.com/docs/reference). It
   requires to set the `WORKOS_API_KEY` environment variable to your WorkOS
   secret key."
  [request]
  (http/request
    (assoc-in request
              [:headers
               "Authorization"]
              (str "Bearer "
                   (System/getenv "WORKOS_API_KEY")))))

(defn add-workos-client
  [w]
  (assoc w
         :workos/client
         #'workos-request))

(defn get-jwks
  "Fetches the JSON Web Key Set from the public WorkOS endpoint for the given `client-id`-"
  [w]
  (-> {:request-method :get
       :url (str "https://api.workos.com/sso/jwks/"
                 (:workos/client-id w))
       :as :json}
      (http/request)
      (get-in [:body :keys])))

(defn add-jwk-public-key
  [w]
  (assoc w
         :auth/jwk-public-key
         (first (get-jwks w))))

(defn add-client-id
  [w]
  (assoc w
         :workos/client-id
         (System/getenv "WORKOS_CLIENT_ID")))

(defn add-get-api-key
  [w]
  (assoc w
         :workos/get-api-key
         (fn []
           (System/getenv "WORKOS_API_KEY"))))

(defn unsign
  ([key access-token options]
   (jwt/unsign access-token
               (buddy-jwk/jwk->public-key key)
               (merge {:alg (keyword (str/lower-case (:alg key)))}
                      options)
               )))

(defn try-unsign
  [key access-token options]
  (try
    (unsign key
            access-token
            options)
    (catch clojure.lang.ExceptionInfo e
      (assoc (ex-data e)
             :message
             (.getMessage e)))))

(defn add-jwt
  [w]
  (assoc w
         :auth/jwt
         (try-unsign (:auth/jwk-public-key w)
                     (:auth/access-token w)
                     (:auth/jwt-unsign-options w))))

(defn add-access-token
  [w]
  (assoc w
         :auth/access-token
         (get-in w
                 [:ring/request
                  :cookies
                  "workos-access-token"
                  :value])))

(defn add-refresh-token
  [w]
  (assoc w
         :auth/refresh-token
         (get-in w
                 [:ring/request
                  :cookies
                  "workos-refresh-token"
                  :value])))

(defn logout-redirect-url
  [w]
  (when (get-in w
                [:auth/jwt
                 :sid])
    (-> "https://api.workos.com/user_management/sessions/logout"
        (uri/uri)
        (uri/assoc-query :session_id
                         (get-in w
                                 [:auth/jwt
                                  :sid]))
        (str))))

(defn organization-request
  [{:keys [organization/uuid]}]
  {:request-method :get
   :url (str "https://api.workos.com/organizations/external_id/"
             uuid)
   :as :json
   :unexceptional-status #{200 404}
   })

(defn add-organization-request
  [w]
  (assoc w
         :organization-request
         (organization-request w)))

(defn add-organization-response
  [w]
  (assoc w
         :organization-response
         (try
           ((:workos/client w) (:organization-request w))
           (catch Exception e
             (throw (ex-info "organization-request failed"
                             {:w w}
                             e))))))

(defn add-workos-organization-id
  [w]
  (assoc w
         :workos/organization-id
         (get-in w
                 [:organization-response
                  :body
                  :id])))

(defn refresh-token-request
  [w]
  {:request-method :post
   :url "https://api.workos.com/user_management/authenticate"
   :form-params {:client_id (:workos/client-id w)
                 :client_secret ((:workos/get-api-key w))
                 :grant_type "refresh_token"
                 :refresh_token (:auth/refresh-token w)
                 :organization_id (:workos/organization-id w)
                 :ip_address (:remote-addr (:ring/request w))
                 :user_agent (get-in w
                                     [:ring/request
                                      :headers
                                      "user-agent"])}
   :unexceptional-status #{200 400}
   :content-type :json
   :as :json})

(defn add-refresh-token-request
  [w]
  (assoc w
         :refresh-token-request
         (refresh-token-request w)))

(defn add-refresh-token-response
  [w]
  (assoc w
         :refresh-token-response
         (when (:auth/refresh-token w)
           ((:workos/client w) (:refresh-token-request w)))))

(defn get-refresh-auth
  [w]
  (when (= (:status (:refresh-token-response w))
           200)
    {:auth/access-token (get-in w
                                [:refresh-token-response
                                 :body
                                 :access_token])
     :auth/refresh-token (get-in w
                                 [:refresh-token-response
                                  :body
                                  :refresh_token])}))

(defn refresh-auth
  [w]
  (merge w
         (get-refresh-auth w)))

(defn prepare-refresh
  [w]
  (-> w
      (add-workos-client)
      (add-organization-request)
      (add-organization-response)
      (add-workos-organization-id)
      (add-refresh-token-request)))

(defn refresh-if-necessary
  [w]
  (if (= (:cause (:auth/jwt w))
         :exp)
    (-> w
        (prepare-refresh)
        (add-refresh-token-response)
        (refresh-auth))
    w))

(defn list-user-request
  [w]
  {:request-method :get
   :url "https://api.workos.com/user_management/users"
   :query-params {:email (:user/email w)
                  :limit 1}
   :content-type :json
   :as :json})

(defn add-list-user-request
  [w]
  (assoc w
         :list-user-request
         (list-user-request w)))

(defn add-list-user-response
  [w]
  (assoc w
         :list-user-response
         ((:workos/client w) (:list-user-request w))))

(defn add-workos-user-id
  [w]
  (assoc w
         :workos/user-id
         (:id (first (:data (:body (:list-user-response w)))))))
