(ns parts.routine.core
  "Manage periodic routines in the system:

  - Discover and validate routines from the registry

  - Schedule each on light or heavy executors with fixed delays

  - Provide start!, stop! and idle? functions to control the lifecycle of the routines."
  (:require [parts.routine.schedulers :as schedulers]))

(defonce active-routines-count
  (delay
    (java.util.concurrent.atomic.AtomicLong. 0)))

(defn validate-routines
  "Validates all routine entries in the system register."
  [w]
  ;; TODO: use clojure.spec to validate it:
  (doseq [routine (filter :routine/fn
                          ((:system/get-register w)))
          k [:routine/category
             :routine/fn
             :routine/interval-ms]]
    (when-not (get routine k)
      (throw (ex-info (str "Routine map is missing required key " k)
                      {:routine routine
                       :missing-key k})))))

(defn call-routine!
  "Calls the `:routine/fn`. Catches all `Throwable`s and logs an error, since a
   `:routine/fn` should usually catch all `Throwable`s itself.

   Increments the `active-routines-count` when the routine starts and decrements
   it when the routine is done."
  [w]
  (.incrementAndGet @active-routines-count)
  (try
    ((:routine/fn (:routine w)))
    (catch Throwable e
      ;; If `scheduleWithFixedDelay` encounters an exception it suppresses
      ;; subsequent executions. Therefore any errors are catched and logged here
      ;; to keep executing the `routine-fn`, even if it contains a bug and does
      ;; not catch all exceptions:
      ((:log/log w
                 identity)
       {:log/error :routine/unhandled-exception
        :message "unhandled exception - a routine-fn should handle all exceptions"
        :routine (:routine w)
        :exception e})
      )
    (finally
      (.decrementAndGet @active-routines-count))))

(defn schedule-routine!
  "Schedules a routine on the `:routine/scheduled-executor`, which delegates the
   routine task to the `:routine/heavy-routine-executor` or
   `:routine/light-routine-executor` executor."
  [w]
  (let [routine (:routine w)]
    (.scheduleWithFixedDelay (:routine/scheduled-executor w)
                             (fn []
                               (case (:routine/category routine)
                                 :heavy
                                 (.execute (:routine/heavy-routine-executor w)
                                           (fn []
                                             (call-routine! w)))
                                 :light
                                 (.execute (:routine/light-routine-executor w)
                                           (fn []
                                             (call-routine! w)))))
                             (:routine/interval-ms routine)
                             (:routine/interval-ms routine)
                             java.util.concurrent.TimeUnit/MILLISECONDS)))

(defn shutdown-and-await-termination
  "Shutdowns a `java.util.concurrent.ExecutorService` gracefully. Based
   on example `shutdownAndAwaitTermination` from
   https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html"
  [w]
  ;; Disable new tasks from being submitted:
  (let [^java.util.concurrent.ExecutorService scheduler (:executor w)]
    (.shutdown scheduler)
    (try
      ;; Wait a while for existing tasks to terminate:
      (when-not (.awaitTermination scheduler
                                   (or (::termination-timeout w)
                                       (* 1000 30))
                                   java.util.concurrent.TimeUnit/MILLISECONDS)
        ;; Cancel currently executing tasks:
        (.shutdownNow scheduler)
        ;; Wait a while for tasks to respond to being cancelled:
        (when-not (.awaitTermination scheduler
                                     (or (::force-termination-timeout w)
                                         (* 1000 30))
                                     java.util.concurrent.TimeUnit/MILLISECONDS)
          ((:log/log w
             identity)
           {:log/error ::termination-failed
            :executor scheduler})))
      (catch InterruptedException _e
        ;; (Re-)Cancel if current thread also interrupted:
        (.shutdownNow scheduler)
        ;; In comparison to the example, do not interrupt the Thread
        ;; that executes the system shutdown:
        ;;
        ;; (.interrupt (Thread/currentThread))
        ))))

(defn schedule-routines!
  [w]
  (doseq [routine (filter
                    (fn [routine]
                      (:routine/fn routine))
                    ((:system/get-register w)))]
    (schedule-routine! (assoc w
                              :routine routine)))
  w)

(defn idle?
  "Returns true, if no routines are currently running on any Thread."
  [_w]
  (zero? @active-routines-count))

(defn start!
  [w]
  (validate-routines w)
  (-> w
      (schedulers/add-heavy-routine-executor)
      (schedulers/add-light-routine-executor)
      (schedulers/add-scheduled-executor)
      (schedule-routines!)))

(defn stop!
  [w]
  (doseq [executor (keep
                     (fn [key]
                       (get w
                            key))
                     [:routine/scheduled-executor
                      :routine/light-routine-executor
                      :routine/heavy-routine-executor])]
    (shutdown-and-await-termination (assoc w
                                           :executor executor))))
