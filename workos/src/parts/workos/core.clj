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
