;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.workflow-lock
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.signals :as s]
            [temporal.test.utils :as t])
  (:import [java.time Duration]))

(use-fixtures :once t/wrap-service)

(defactivity slow-activity
  [ctx {:keys [delay-ms value]}]
  (Thread/sleep delay-ms)
  value)

(defworkflow lock-workflow
  [args]
  (let [state (atom {:values []})
        lock (w/new-lock)
        signals (s/create-signal-chan)]
    (w/register-update-handler!
     (fn [update-type {:keys [value] :as args}]
       (log/info "lock-handler: type=" update-type "args=" args)
       (case update-type
         :add-with-lock
         (w/with-lock lock
           ;; Read current state, do slow operation, then update
           (let [current (:values @state)
                 result @(a/invoke slow-activity {:delay-ms 10 :value value}
                                   {:start-to-close-timeout (Duration/ofSeconds 5)})]
             (swap! state update :values conj result)
             @state))
         :get-state @state
         nil)))
    ;; Wait for done signal
    (s/<! signals ::done)
    @state))

(deftest lock-test
  (testing "Verifies that WorkflowLock works for coordinating handlers"
    (let [workflow (t/create-workflow lock-workflow)]
      (c/start workflow {})
      ;; Add a value using the lock
      (let [result (c/update workflow :add-with-lock {:value "first"})]
        (is (= {:values ["first"]} result)))
      ;; Add another value
      (let [result (c/update workflow :add-with-lock {:value "second"})]
        (is (= {:values ["first" "second"]} result)))
      ;; Signal workflow to complete
      (c/>! workflow ::done {})
      ;; Verify final workflow result
      (is (= {:values ["first" "second"]} @(c/get-result workflow))))))
