(ns parts.workos.create-organization
  "[Creates a WorkOS
   organization](https://workos.com/docs/reference/user-management/organization-membership/create)
   it is idempotent, meaning it will do nothing if the corresponding organization
   already exists."
  (:require [parts.workos.core :as workos]
            ))

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

(defn add-organization-name
  [w]
  (assoc w
         :organization/name
         (or (:organization/name w)
             (str "Organization of "
                  (:user/email w)))))

(defn create-organization-request
  [params]
  {:request-method :post
   :url "https://api.workos.com/organizations"
   :form-params {:name (:organization/name params)
                 :external_id (str (:organization/uuid params))
                 }
   :content-type :json
   :as :json})

(defn add-create-organization-request
  [w]
  (assoc w
         :create-organization-request
         (create-organization-request w)))

(defn add-user-id
  [w]
  (assoc w
         :user-id
         (get-in w
                 [:list-user-response
                  :body
                  :data
                  0
                  :id])))

(defn prepare
  [w]
  (-> w
      (workos/add-workos-client)
      (add-organization-name)
      (add-organization-request)
      (add-organization-response)
      (add-create-organization-request)
      (workos/add-list-user-request)
      (workos/add-list-user-response)
      (add-user-id)
      ))

(defn effect!
  [w]
  (if (= (get-in w
                 [:organization-response
                  :status])
         404)
    (assoc w
           :create-organization-response
           ;; Only create a WorkOS organization if the WorkOS with the given
           ;; `:user/email` exists:
           (when (:user-id w)
             ((:workos/client w)
              (:create-organization-request w))))
    w))

(defn create-organization!
  [w]
  (-> w
      (prepare)
      (effect!)))
