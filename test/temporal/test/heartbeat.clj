;; Copyright © 2023 Manetu, Inc.  All rights reserved
;;
(ns temporal.test.heartbeat
  (:require [clojure.test :refer :all]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity heartbeat-activity
  [_ _]
  (if-let [details (a/get-heartbeat-details)]
    details
    (do
      (a/heartbeat :ok)
      (throw (ex-info "heartbeat details not found" {})))))

(defworkflow heartbeat-workflow
  [_]
  @(a/invoke heartbeat-activity {}))

(deftest the-test
  (testing "Verifies that heartbeats are handled properly"
    (let [workflow (t/create-workflow heartbeat-workflow)]
      (c/start workflow {})
      (is (-> workflow c/get-result deref (= :ok))))))
