(ns parts.routine.schedulers)

;; There should only be 2 threads that work on heavy routines
;; like rendering a video.

(def ^:private default-heavy-routine-pool-size 2)

(defn add-heavy-routine-scheduler
  [w]
  (assoc w
         :routine/heavy-routine-scheduler
         (java.util.concurrent.Executors/newScheduledThreadPool (::heavy-routine-pool-size w
                                                                  default-heavy-routine-pool-size))))

;; Tasks that are rather light, should be executed instantly by
;; executing them in  virtual threads.

(def ^:private virtual-thread-factory
  (.factory (Thread/ofVirtual)))

(defn add-light-routine-scheduler
  [w]
  (assoc w
         :routine/light-routine-scheduler
         (java.util.concurrent.Executors/newScheduledThreadPool 1
                                                                virtual-thread-factory)))
