(ns parts.routine.schedulers
  "Provides executors for running routine tasks:

   - Heavy routines (e.g. video rendering) are handled by a ThreadPool (default
     size 2, configurable via `::heavy-routine-pool-size`).

   - Light routines (short-lived, not CPU or memory intensive) are executed on a
     virtual thread for or non-blocking tasks.")

(def default-heavy-routine-pool-size 2)

(defn add-heavy-routine-executor
  [w]
  (assoc w
         :routine/heavy-routine-executor
         (java.util.concurrent.ThreadPoolExecutor.
           ;; corePoolSize:
           0
           ;; maximumPoolSize:
           (or (::heavy-routine-pool-size w)
               default-heavy-routine-pool-size)
           ;; keepAliveTime:
           1
           ;; in seconds:
           java.util.concurrent.TimeUnit/SECONDS
           ;; a LinkedBlockingQueue with a capacity of Integer.MAX_VALUE:
           (java.util.concurrent.LinkedBlockingQueue.)
           )))

(defn add-light-routine-executor
  [w]
  (assoc w
         :routine/light-routine-executor
         (java.util.concurrent.Executors/newVirtualThreadPerTaskExecutor)))

(defn add-scheduled-executor
  [w]
  (assoc w
         :routine/scheduled-executor
         (java.util.concurrent.Executors/newSingleThreadScheduledExecutor)))
