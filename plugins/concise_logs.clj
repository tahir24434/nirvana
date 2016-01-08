(include "../pg_utils.clj")
(require '[plugins.director-bootup-stage1 :as DBS1])


; Take a raw stream and create a PG stream message out of it
(defn pg-stream [& children]
  (fn [e]
    (if (and (check-tags "NRV" (:tags e)) (not= nil (:description e))) ; REVIEW: Do we really need NRV tag ???
      ; "2016-01-04T23:16:43.021Z 127.0.0.1 {2016-01-04T23:16:43.019273+00:00,tahir-ahmed-b-1-bld-master, service_directory_2fa5b111 [10c044ae:1:9]<352:05:17:26.955312>[17]: [init]: rest_gateway is active}
      (let [search-msg (get-substr-after-nth-occurance-of-char (:description e) ", " 1)] ; Get PG log message out of syslog message
        (let [new-event (process-raw-PG-msg e search-msg)]
          (do
            (if (some? (:handler new-event)) (call-rescue new-event children))
            )
          )
        )
      )
    )
  )

(let [index (index)]
  (streams
    (where (not (expired? event))
      (default :ttl 10
        (pg-stream
          (where (re-find #"SMLite_.*" (:handler event))
            (DBS1/director-bootup-stage1)
            )
          )
        )
      )
    (expired
      #(prn "******* Expired1> ******** " %)
      (where (re-find #"director_bootup_stage1" (:service event))
        (DBS1/director-bootup-stage1-expired)
        )
      (where (re-find #"director_bootup_stage2" (:service event))
        (DBS2/director-bootup-stage2-expired)
        )
      )
    )
  )
