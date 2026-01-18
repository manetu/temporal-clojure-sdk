;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.handler-completion
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.signals :as s]
            [temporal.test.utils :as t])
  (:import [java.time Duration]))

(use-fixtures :once t/wrap-service)

(defactivity completion-activity
  [ctx {:keys [value]}]
  value)

(defworkflow handler-completion-workflow
  [args]
  (let [state (atom {:values []})
        signals (s/create-signal-chan)]
    (w/register-update-handler!
     (fn [update-type {:keys [value] :as args}]
       (log/info "handler: type=" update-type "args=" args)
       (case update-type
         :add
         (let [result @(a/invoke completion-activity {:value value}
                                 {:start-to-close-timeout (Duration/ofSeconds 5)})]
           (swap! state update :values conj result)
           @state)
         nil)))
    ;; Wait for trigger signal to start completion
    (s/<! signals ::trigger)
    ;; Wait for all handlers to finish before returning
    (w/await w/every-handler-finished?)
    @state))

(deftest handler-completion-test
  (testing "Verifies that every-handler-finished? works correctly"
    (let [workflow (t/create-workflow handler-completion-workflow)]
      (c/start workflow {})
      ;; Send an update
      (let [result (c/update workflow :add {:value "test"})]
        (is (= {:values ["test"]} result)))
      ;; Signal workflow to complete - it should wait for handlers
      (c/>! workflow ::trigger {})
      ;; Verify final workflow result includes the update
      (is (= {:values ["test"]} @(c/get-result workflow))))))
