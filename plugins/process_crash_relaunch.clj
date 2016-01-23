(include "../pg_utils.clj")
(include "./start_states_consts.clj")

(ns plugins.process-crash-relaunch
  "process crash handlers"
  (:require [riemann.config :refer :all]
            [clojure.string :as str]
            [riemann.streams :refer :all]))

(defn process-crash-relaunch []
  (fn [e]
    (def start-state (get start-states-map :process-crash-relaunch-start-state))
    (def plugin-type "process_crash_relaunch")
    (process-state-ek1-mk2val
      start-state [] e
      ; system_manager_0fdcc4e1 [655b874e:1903:8]<012:03:05:05.350066>[118]: [process_child_exit]: ippid 10.22.27.51:1765 Exited service pem_master pgname pem_master_6543e201
      #"\[process_child_exit\]: ippid (\S+):(\d+) Exited service (\S+) pgname (\S+)"
      "Exited service message."
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :plugin_type plugin-type
          :ip (get regex-match 1)
          :pid (get regex-match 2)
          :exited_service (get regex-match 3)
          :pgname (get regex-match 4)
          :ttl 10
          )
        )
      )
    (process-state-ek1-mk2val
      2001 [2000] e
      ;system_manager_0fdcc4e1 [655b874e:1903:9]<012:03:05:05.350125>[119]: [process_child_exit]: SIGCHILD child 1765 returned raw status 0x200 and exit type=[WIFEXITED: Terminated normally with status 2]
      #"\[process_child_exit\]: SIGCHILD child (\d+) returned raw status (\S+).*"
      "Returned raw status of exit service message."
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :raw_status (get regex-match 2)
          )
        )
      )
    (process-state-ek1-mk2val
      2002 [2001] e
      ;system_manager_0fdcc4e1 [655b874e:1903:5]<012:03:05:05.350205>[120]: [process_child_exit]: Program /opt/pg/bin/pem_master/9.44.0/launch_pem_master PID 1765 has exited with status = 512
      #"\[process_child_exit\]: Program (\S+) PID (\d+) has exited with status = (\d+)"
      "Exited process pgname"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :version (get (str/split (get regex-match 1) #"/") 5)
          :exit_status (get regex-match 3)
          )
        )
      )
    (process-state-ek1-mk2val
      2003 [2002] e
      ; HealthMonitorProcessFailure [655b874e:1903:9]<012:03:05:05.350400>[1]: [report_process_failure]: pgname pem_master_6543e201 fail_from_exit 1 is_crash 1 sm_exit 0 ip_pid 10.22.27.51:1765
      #"\[report_process_failure\]: pgname (\S+) fail_from_exit .* ip_pid (\S+):(\d+)"
      "HM report failures"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (assoc strm
          :pgname (get regex-match 1)
          :ip (get regex-match 2)
          )
        )
      )
    (process-state-ek1-mk2val
      2004 [2003] e
      ; HealthMonitorProcessFailure [655b874e:1903:9]<012:03:05:05.354306>[7]: [report_process_failure]: health_status_set_dead for pem_master_6543e201 DONE
      #"\[report_process_failure\]: health_status_set_dead for (\S+) DONE"
      "HM set status as dead"
      "pgtxn"
      plugin-type
      )
    (process-state-ek1-mk2val
      2005 [2004] e
      ; service_directory_37482861 [655b874e:539:9]<012:03:05:05.357860>[244]: [report_process_failure]: informed of service pem_master_6543e201 failure reason 2
      #"\[report_process_failure\]: informed of service (\S+) failure reason (\d+).*"
      "SD sees the failure"
      "pgtxn"
      plugin-type
      )
    (process-state-ek1-mk2val
      2006 [2005] e
      ; service_directory_37482861 [655b874e:539:9]<012:03:05:05.359302>[250]: [report_process_failure]: process_death service pem_master_6543e201 done with status 'ok'
      #"\[report_process_failure\]: process_death service (\S+) done with status .*"
      "SD process failure"
      "pgtxn"
      plugin-type
      )
    (process-state-ek1-mk2val
      2007 [2006] e
      ; service_directory_37482861 [655b874e:539:9]<012:03:05:05.960330>[301]: [report_process_failure]: relaunched service pem_master_6543e201 successfully
      #"\[report_process_failure\]: relaunched service (\S+) successfully"
      "Service relaunched successfully"
      "pgtxn"
      plugin-type
      (fn [strm event regex-match]
        (do
          ; process_pid with service name bridge and pg_name bridge_890xe was killed on node 2.1.1.12 with exit status 0x9. It was internall killed.
          (prn "start-time=" (:startTime strm) "current-time=" (:time strm))
          (def concise-msg (apply str (now) ": Service " (:exited_service strm) " with pgname " (:pgname strm)
                             " on node " (:ip strm) " with pid:" (:pid strm) " was killed with status " (:exit_status strm)
                             " and was recovered successfully. The recovery took " (get-time-taken (:startTime strm) (:time strm)) " seconds"
                             )
            )
          )
        (def exit-status (:exit_status strm))
        (if (not= exit-status "0")
          (file-write info-log-location [concise-msg "\n"])
        )
          "DELETE"
        )
      )
    )
  )


(defn process-crash-relaunch-expired [& children]
  (fn [strm]
    (prn "event is expired")
    (let [concise-msg (apply str (now) ": Service " (:exited_service strm) " with pgname " (:pgname strm)
                " on node " (:ip strm) " with pid:" (:pid strm) " was killed with status " (:exit_status strm)
                " and could NOT be recovered"
                ) exit-status (:exit_status strm)]
      (if (not= exit-status "0")
        (file-write error-log-location [concise-msg "\n"])
        )
      )
    )
  )
