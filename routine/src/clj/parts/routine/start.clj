(ns parts.routine.start
  (:require [parts.routine.core :as core]
            [parts.routine.schedulers :as schedulers]))

(defn schedule-light-routines!
  [w]
  (let [light-routines (filter
                         (fn [routine]
                           (= (:routine/category routine) :light))
                         (:routines w))
        scheduled-routines (doall (map (fn [r]
                                         (assoc r
                                                :routine/scheduled-future
                                                (core/schedule-routine! (:routine/light-routine-scheduler w)
                                                                        r)))
                                       light-routines))]
    (assoc w
           :routine/scheduled-light-routines
           scheduled-routines)))

(defn schedule-heavy-routines!
  [w]
  (let [heavy-routines (filter
                         (fn [routine]
                           (= (:routine/category routine) :heavy))
                         (:routines w))
        scheduled-routines (doall (map (fn [r]
                                         (assoc r
                                                :routine/scheduled-future
                                                (core/schedule-routine! (:routine/heavy-routine-scheduler w)
                                                                        r)))
                                       heavy-routines))]
    (assoc w
           :routine/scheduled-heavy-routines
           scheduled-routines)))

(defn start!
  [w]
  (-> w
      (core/add-routines)
      (schedulers/add-heavy-routine-scheduler)
      (schedulers/add-light-routine-scheduler)
      (schedule-light-routines!)
      (schedule-heavy-routines!)))
