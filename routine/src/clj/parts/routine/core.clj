(ns parts.routine.core)

(defn find-routine-def
  [register routine]
  (when-let [kind (:routine/kind routine)]
    (some
      (fn [entry]
        (when (and (:routine/fn entry)
                   (= (:routine/kind entry)
                      kind))
          entry))
      register)))

(defn add-routines
  [w]
  (assoc w
         :routines
         (filter
           (fn [routine]
             (when (:routine/kind routine)
               (assert (:routine/fn routine))
               (assert (:routine/interval-ms routine))
               (assert (:routine/category routine))))
           ((:system/get-register w)))))

;; Example: run a task every 5 seconds with an initial 1 second delay
(defn schedule-routine!
  [scheduler routine]
  (.scheduleWithFixedDelay scheduler
                           (fn []
                             (try
                               (:routine/fn routine)
                               (catch Throwable e
                                 ;; If `scheduleWithFixedDelay`
                                 ;; encounters an exception it
                                 ;; suppresses subsequent
                                 ;; executions. Therefore any errors
                                 ;; are catched and logged here to
                                 ;; keep executing the `routine-fn`,
                                 ;; even if it contains a bug and does
                                 ;; not catch all exceptions:

                                 )))
                           0
                           (:routine/interval-ms routine)
                           java.util.concurrent.TimeUnit/MILLISECONDS))

(def termination-timeout (* 1000 30))

(defn shutdown-and-await-termination
  "Shutdowns a `java.util.concurrent.ExecutorService` gracefully. Based
   on example `shutdownAndAwaitTermination` from
   https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html"
  [^java.util.concurrent.ExecutorService scheduler]
  ;; Disable new tasks from being submitted:
  (.shutdown scheduler)
  (try
    ;; Wait a while for existing tasks to terminate:
    (when-not (.awaitTermination scheduler
                                 termination-timeout
                                 java.util.concurrent.TimeUnit/MILLISECONDS)
      ;; Cancel currently executing tasks:
      (.shutdownNow scheduler)
      ;; Wait a while for tasks to respond to being cancelled:
      (when-not (.awaitTermination scheduler
                                   termination-timeout
                                   java.util.concurrent.TimeUnit/MILLISECONDS)
        (.println System/err
                  "Scheduler did not terminate.")))
    (catch InterruptedException e
      ;; (Re-)Cancel if current thread also interrupted:
      (.shutdownNow scheduler)
      ;; In comparison to the example, do not interrupt the Thread
      ;; that executes the system shutdown:
      ;;
      ;; (.interrupt (Thread/currentThread))
      )
    )
  )

