(ns parts.replicant.command
  "Utils to handle data-driven commands. Please read:

   https://replicant.fun/tutorials/network-writes/")

(defn add-log-entry [log entry]
  (cons entry log))

(defn issue-command [state now command]
  (update-in state [:ui.command/log command] add-log-entry
             {:command/status :command.status/issued
              :command/user-time now}))

(defn receive-response [state now command response]
  (update-in state [:ui.command/log command] add-log-entry
             (cond-> {:command/status (if (:success? response)
                                      :command.status/success
                                      :command.status/error)
                      :command/user-time now}
               (:result response)
               (assoc :command/result (:result response)))))

(defn get-log [state command]
  (get-in state [:ui.command/log command]))

(defn get-latest
  [state command]
  (first (get-log state command)))

(defn get-latest-status [state command]
  (:command/status (get-latest state command)))

(defn get-latest-result [state command]
  (:command/result (get-latest state command)))

(defn get-latest-error [state command]
  (:error (get-latest-result state command)))

(defn issued? [state command]
  (= :command.status/issued
     (get-latest-status state command)))

(defn success? [state command]
  (->> (get-log state command)
       (some (comp #{:command.status/success} :command/status))
       boolean))

(defn error? [state command]
  (= :command.status/error
     (get-latest-status state command)))

(defn issued-at [state command]
  (->> (get-log state command)
       (drop-while #(not= :command.status/issued (:command/status %)))
       first
       :command/user-time))
