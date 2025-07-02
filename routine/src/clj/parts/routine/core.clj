(ns parts.routine.core
  "Manage periodic routines in the system:

  • Discover and validate routines from the registry
  • Schedule each on light or heavy executors with fixed delays
  • Provide start!, stop! and idle? functions to control the lifecycle of the routines."
  (:require [parts.routine.schedulers :as schedulers]))

(defn get-routines
  [w]
  (filter :routine/kind
    ((:system/get-register w))))

(defn check-routines
  [w]
  (doseq [routine (get-routines w)
          k [:routine/category
             :routine/fn
             :routine/interval-ms]]
    (when-not (get routine k)
      (throw (ex-info (str "Routine map is missing required key " k)
                      {:routine routine
                       :missing-key k})))))

(defn schedule-routine!
  [w]
  (let [routine (:routine w)]
    (.scheduleWithFixedDelay (:scheduler w)
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
                                   ((:log/log w
                                      identity)
                                    {:log/error :routine/unhandled-exception
                                     :message "unhandled exception - a routine-fn should handle all exceptions"
                                     :routine routine
                                     :exception e})
                                   )))
                             0
                             (:routine/interval-ms routine)
                             java.util.concurrent.TimeUnit/MILLISECONDS)))

(defn shutdown-and-await-termination
  "Shutdowns a `java.util.concurrent.ExecutorService` gracefully. Based
   on example `shutdownAndAwaitTermination` from
   https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html"
  [w]
  ;; Disable new tasks from being submitted:
  (let [^java.util.concurrent.ExecutorService scheduler (:scheduler w)]
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
           {:log/error :scheduler/termination-failed
            :scheduler scheduler})))
      (catch InterruptedException e
        ;; (Re-)Cancel if current thread also interrupted:
        (.shutdownNow scheduler)
        ;; In comparison to the example, do not interrupt the Thread
        ;; that executes the system shutdown:
        ;;
        ;; (.interrupt (Thread/currentThread))
        ))))

(defn schedule-light-routines!
  [w]
  (let [light-routines (filter
                         (fn [routine]
                           (= (:routine/category routine) :light))
                         (get-routines w))]
    (doseq [routine light-routines]
      (schedule-routine! (assoc w
                                :scheduler (:routine/light-routine-scheduler w)
                                :routine routine)))
    w))

(defn schedule-heavy-routines!
  [w]
  (let [heavy-routines (filter
                         (fn [routine]
                           (= (:routine/category routine) :heavy))
                         (get-routines w))]
    (doseq [routine heavy-routines]
      (schedule-routine! (assoc w
                                :scheduler (:routine/heavy-routine-scheduler w)
                                :routine routine)))
    w))

(defn idle?
  "Check if there are active threads at the moment. When there are no active threads, "
  [w]
  (and (or (not (:routine/heavy-routine-scheduler w))
           (zero? (.getActiveCount (:routine/heavy-routine-scheduler w))))
       (or (not (:routine/light-routine-scheduler w))
           (zero? (.getActiveCount (:routine/light-routine-scheduler w))))))

(defn start!
  [w]
  (check-routines w)
  (-> w
      (schedulers/add-heavy-routine-scheduler)
      (schedulers/add-light-routine-scheduler)
      (schedule-light-routines!)
      (schedule-heavy-routines!)))

(defn stop!
  [w]
  (when-let [heavy-routine-scheduler (:routine/heavy-routine-scheduler w)]
    (shutdown-and-await-termination (assoc w
                                           :scheduler heavy-routine-scheduler)))
  (when-let [light-routine-scheduler (:routine/light-routine-scheduler w)]
    (shutdown-and-await-termination (assoc w
                                           :scheduler light-routine-scheduler)))
  (for [routine (:routines w)]
    (when-let [stop-fn (:routine/stop-fn routine)]
      (stop-fn)))
  )
