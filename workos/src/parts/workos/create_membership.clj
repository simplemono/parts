(ns parts.workos.create-membership
  "Creates an [organization
   membership](https://workos.com/docs/reference/user-management/organization-membership).
   It is idempotent, meaning it will do nothing if the corresponding membership
   already exists."
  (:require [parts.workos.create-organization :as create-organization]
            [parts.workos.core :as workos]
            ))

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

(defn add-organization-id
  [w]
  (assoc w
         :organization-id
         (get-in w
                 [:organization-response
                  :body
                  :id])))

(defn membership-request
  [w]
  {:request-method :get
   :url "https://api.workos.com/user_management/organization_memberships"
   :query-params {:user_id (:user-id w)
                  :organization_id (:organization-id w)
                  :limit 1}
   :content-type :json
   :as :json})

(defn add-membership-request
  [w]
  (assoc w
         :membership-request
         (membership-request w)))

(defn add-membership-response
  [w]
  (assoc w
         :membership-response
         (when (:organization-id w)
           ((:workos/client w) (:membership-request w)))))

(defn add-create-membership
  [w]
  (assoc w
         :create-membership
         (and
           ;; Only create the membership if the corresponding user exists:
           (:user-id w)

           ;; and only if the membership does not exist yet:
           (empty? (get-in w
                           [:membership-response
                            :body
                            :data])))))

(defn create-membership-request
  [w]
  {:request-method :post
   :url "https://api.workos.com/user_management/organization_memberships"
   :form-params {:user_id (:user-id w)
                 :organization_id (:organization-id w)}
   :content-type :json
   :as :json})

(defn add-create-membership-request
  [w]
  (assoc w
         :create-membership-request
         (create-membership-request w)))

(defn prepare
  [w]
  (-> w
      (workos/add-workos-client)
      (workos/add-list-user-request)
      (workos/add-list-user-response)
      (add-user-id)
      (create-organization/add-organization-request)
      (create-organization/add-organization-response)
      (add-organization-id)
      (add-membership-request)
      (add-membership-response)
      (add-create-membership)
      (add-create-membership-request)))

(defn effect!
  [w]
  (if (:create-membership w)
    (assoc w
           :create-membership-response
           (try
             ((:workos/client w) (:create-membership-request w))
             (catch Exception e
               (throw
                 (ex-info "create-membership effect failed"
                          {:w w}
                          e)))))
    w))

(defn create-membership!
  [w]
  (-> w
      (prepare)
      (effect!)))
