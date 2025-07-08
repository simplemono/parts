(ns parts.workos.cookies)

(defn workos-cookies
  "The auth cookies used for the WorkOS integration. The `workos-access-token`
   cookie is an JWT. The `workos-refresh-token` cookies stores the refresh-token
   that can be used to get a new access-token, when the old one is expired."
  [w]
  {"workos-access-token"
   {
    :value (:auth/access-token w)
    :domain (:workos/cookie-domain w)
    :path "/"
    :max-age (* 60 60 24 30)
    :secure true
    :http-only true
    :same-site :lax
    ;; See
    ;; https://blog.viadee.de/samesite-cookies-strict-oder-lax
    ;; for possible problems with `:strict`. When the
    ;; redirect here targeted the `/team` page, then the
    ;; cookie was not readable whereby the auth check
    ;; failed. However, with the redirect target `/` this
    ;; does not happen.
    }
   "workos-refresh-token"
   {
    :value (:auth/refresh-token w)
    :domain (:workos/cookie-domain w)
    :path "/"
    :max-age (* 60 60 24 30)
    :secure true
    :http-only true
    :same-site :lax
    }})
