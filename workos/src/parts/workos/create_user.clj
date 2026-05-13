(ns parts.workos.create-user
  "[Creates a WorkOS
   user](https://workos.com/docs/reference/user-management/user/create/). It is
   idempotent, meaning it will do nothing if the user with the corresponding
   email already exists."
  (:require [parts.workos.core :as workos]
            ))

(defn user-create-request
  [w]
  {:request-method :post
   :url "https://api.workos.com/user_management/users"
   :form-params {:email (:user/email w)}
   :content-type :json
   :as :json})

(defn add-user-create-request
  [w]
  (assoc w
         :user-create-request
         (user-create-request w)))

(defn add-create-user
  [w]
  (assoc w
         :create-user
         (empty? (get-in w
                         [:list-user-response
                          :body
                          :data]))))

(defn prepare
  [w]
  (-> w
      (workos/add-workos-client)
      (workos/add-list-user-request)
      (workos/add-list-user-response)
      (add-user-create-request)
      (add-create-user)))

(defn effect!
  [w]
  (if (:create-user w)
    (assoc w
           :user-create-response
           ((:workos/client w)
            (:user-create-request w)))
    w))

(defn create-user!
  [w]
  (-> w
      (prepare)
      (effect!))) 