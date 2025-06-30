(ns parts.routine.schedulers
  (:import (java.util.concurrent Executors)))

;; There should only be 2 thready that work on heavy routines
;; like rendering a video.

(def ^:private heavy-routine-pool-size 2)

(defonce heavy-routine-scheduler
  (future
    (Executors/newScheduledThreadPool heavy-routine-pool-size)))

(defn add-heavy-routine-scheduler
  [w]
  (assoc w
         :routine/heavy-routine-scheduler
         @heavy-routine-scheduler))



;; Tasks that are rather light, should be executed instantly by
;; executing them in  virtual threads.

(def ^:private virtual-thread-factory
  (.factory (Thread/ofVirtual)))

(defonce light-routine-scheduler
  (future
    (Executors/newScheduledThreadPool 1
                                      virtual-thread-factory)))

(defn add-light-routine-scheduler
  [w]
  (assoc w
         :routine/light-routine-scheduler
         @light-routine-scheduler))

(comment
  (core/schedule-routine @light-routine-scheduler
                         (fn [] (println "tick" (java.time.Instant/now)))
                         )

  (.getActiveCount @light-routine-scheduler)
  )