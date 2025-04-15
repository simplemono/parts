(ns parts.replicant.router
  "The client-side routing of the frontend. Please read:

   https://replicant.fun/tutorials/routing/"
  (:require [domkm.silk :as silk]
            [lambdaisland.uri :as uri]))

(defn make-routes [pages]
  (silk/routes
   (mapv
    (fn [{:keys [page-id route]}]
      [page-id route])
    pages)))

(defn url->location [routes url]
  (let [uri (cond-> url (string? url) uri/uri)]
    (when-let [arrived (silk/arrive routes (:path uri))]
      (let [query-params (uri/query-map uri)
            hash-params (some-> uri :fragment uri/query-string->map)]
        (cond-> {:location/page-id (:domkm.silk/name arrived)
                 :location/params (dissoc arrived
                                          :domkm.silk/name
                                          :domkm.silk/pattern
                                          :domkm.silk/routes
                                          :domkm.silk/url)}
          (seq query-params) (assoc :location/query-params query-params)
          (seq hash-params) (assoc :location/hash-params hash-params))))))

(defn ^{:indent 1} location->url [routes {:location/keys [page-id params query-params hash-params]}]
  (cond-> (silk/depart routes page-id params)
    (seq query-params)
    (str "?" (uri/map->query-string query-params))

    (seq hash-params)
    (str "#" (uri/map->query-string hash-params))))

(defn essentially-same? [l1 l2]
  (and (= (:location/page-id l1) (:location/page-id l2))
       (= (not-empty (:location/params l1))
          (not-empty (:location/params l2)))
       (= (not-empty (:location/query-params l1))
          (not-empty (:location/query-params l2)))))

(defn find-target-href [e]
  (some-> e .-target
          (.closest "a")
          (.getAttribute "href")))

(defn get-page-by-id
  [w page-id]
  (some
    (fn [page]
      (when (= (:page-id page)
               page-id)
        page))
    (:ui/pages w)))

(defn get-current-location [routes]
  (->> js/location.href
       (url->location routes)))

(defn get-location-load-actions [w location]
  (when-let [on-load (:on-load (get-page-by-id w
                                               (:location/page-id location)))]
    (on-load location)))

(defn navigate! [{:keys [ui/store ui/event-handler ui/routes] :as w}]
  (let [location (get-current-location routes)
        current-location (:location @store)]
    (when (not= current-location location)
      (event-handler {}
                     (get-location-load-actions w
                                                location)))
    ;; First provide the on-load action handler a chance to prepare
    ;; the store before the page is rendered the first time.
    ;; Otherwise, the changes from the `:on-load` action handler are
    ;; not reflected in the store. For example `loading?` will then
    ;; not work as expected.
    (swap! store assoc :location location)))

(defn route-click
  [{:keys [event ui/store ui/routes] :as w}]
  (let [href (find-target-href event)]
    (when-let [location (url->location routes href)]
      (.preventDefault event)
      (if (essentially-same? location (:location @store))
        (.replaceState js/history nil "" href)
        (.pushState js/history nil "" href))
      (navigate! w)
      (swap! store
             assoc
             :ui/active-url js/location.pathname))))

(defn routing-anchor [attrs children]
  (let [routes (-> attrs :replicant/alias-data :routes)]
    (into [:a (cond-> attrs
                (:ui/location attrs)
                (assoc :href (location->url routes (:ui/location attrs))))]
          children)))
