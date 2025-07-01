(ns parts.routine.schedulers
  "Provides scheduled executors for running routine tasks:

   - Heavy routines (e.g., video rendering) are handled by a fixed-size
     thread pool (default size 2, configurable via ::heavy-routine-pool-size).
   - Light routines are executed immediately on a single virtual thread
     scheduler for short-lived or non-blocking tasks.")

(def ^:private default-heavy-routine-pool-size 2)

(defn add-heavy-routine-scheduler
  [w]
  (assoc w
         :routine/heavy-routine-scheduler
         (java.util.concurrent.Executors/newScheduledThreadPool (::heavy-routine-pool-size w
                                                                  default-heavy-routine-pool-size))))


(def ^:private virtual-thread-factory
  (.factory (Thread/ofVirtual)))

(defn add-light-routine-scheduler
  [w]
  (assoc w
         :routine/light-routine-scheduler
         (java.util.concurrent.Executors/newScheduledThreadPool 1
                                                                virtual-thread-factory)))
