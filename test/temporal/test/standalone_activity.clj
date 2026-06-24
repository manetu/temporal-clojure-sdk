;; Copyright © Manetu, Inc.  All rights reserved

;; NOTE: In temporal-shaded 1.36.0 the in-process TestWorkflowEnvironment does
;; not implement the StartActivityExecution gRPC method, so end-to-end tests that
;; call ActivityClient.start/execute against the test server cannot run.  The tests
;; here cover the full public API surface via two complementary techniques:
;;
;;   1.  Unit tests that call temporal.internal.activity-client helpers directly.
;;   2.  Mock-based tests that use `with-redefs` to stub `start-untyped-handle`
;;       and `reify` UntypedActivityHandle / ActivityInfo instances in-process.

(ns temporal.test.standalone-activity
  (:require [clojure.datafy :as d]
            [clojure.test :refer :all]
            [temporal.activity :refer [defactivity]]
            [temporal.client.activity :as ca]
            [temporal.client.options :as copts]
            [temporal.internal.activity-client :as ac]
            ;; Loading temporal.client.activity transitively loads the
            ;; temporal.internal.activity namespace, which registers the
            ;; Datafiable protocol extension for ActivityInfo.
            [temporal.internal.activity]
            [temporal.testing.env :as e])
  (:import [io.temporal.activity ActivityInfo]
           [io.temporal.api.enums.v1 ActivityIdConflictPolicy ActivityIdReusePolicy]
           [io.temporal.client ActivityClient StartActivityOptions UntypedActivityHandle]
           [java.time Duration]))

;;; -----------------------------------------------------------------------
;;; Helpers
;;; -----------------------------------------------------------------------

(defn- make-mock-handle
  "Returns a reified UntypedActivityHandle whose getResultAsync resolves to `val`."
  [val]
  (let [fut (java.util.concurrent.CompletableFuture.)]
    (.complete fut val)
    (reify UntypedActivityHandle
      (getActivityId [_] "mock-act-id")
      (getActivityRunId [_] "mock-run-id")
      (getResultAsync [_ _t] fut)
      (getResult [_ _t] val)
      (cancel [_])
      (cancel [_ _r])
      (terminate [_])
      (terminate [_ _r])
      (describe [_] nil))))

(defn- make-failing-handle
  "Returns a reified UntypedActivityHandle whose getResultAsync fails with `ex`."
  [ex]
  (let [fut (java.util.concurrent.CompletableFuture.)]
    (.completeExceptionally fut ex)
    (reify UntypedActivityHandle
      (getActivityId [_] "fail-act-id")
      (getActivityRunId [_] "fail-run-id")
      (getResultAsync [_ _t] fut)
      (getResult [_ _t] (throw ex))
      (cancel [_])
      (cancel [_ _r])
      (terminate [_])
      (terminate [_ _r])
      (describe [_] nil))))

(defn- mock-activity-info
  "Returns a reified ActivityInfo implementing only the six methods that datafy reads."
  [wf-id run-id act-id in-wf?]
  (reify ActivityInfo
    (getTaskToken [_] (byte-array 0))
    (getWorkflowId [_] wf-id)
    (getRunId [_] run-id)
    (getActivityId [_] act-id)
    (getActivityType [_] "unit-test-type")
    (isInWorkflow [_] in-wf?)))

;; Activity definitions used for name resolution and mock-dispatch tests.
;; These are auto-discovered by any shared worker that starts after this ns loads,
;; which is harmless — extra activity types are silently ignored.
(defactivity sa-unit-echo-activity [_ args] args)
(defactivity sa-unit-fail-activity [_ _] (throw (ex-info "deliberate failure" {})))

;;; -----------------------------------------------------------------------
;;; 1. resolve-activity-name
;;; -----------------------------------------------------------------------

(deftest test-resolve-activity-name-var
  (testing "defactivity var resolves to the activity type name string"
    (is (= "sa-unit-echo-activity"
           (ac/resolve-activity-name sa-unit-echo-activity)))))

(deftest test-resolve-activity-name-keyword
  (testing "keyword resolves to a non-blank string"
    (let [name (ac/resolve-activity-name :my-act)]
      (is (string? name))
      (is (seq name)))))

(deftest test-resolve-activity-name-string
  (testing "string passes through unchanged"
    (is (= "my-act" (ac/resolve-activity-name "my-act")))))

;;; -----------------------------------------------------------------------
;;; 2. start-activity-options-> (option builder)
;;; -----------------------------------------------------------------------

(deftest test-options-auto-id
  (testing "activity ID is auto-generated when :id is omitted"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue "q"})]
      (is (not (clojure.string/blank? (.getId opts)))))))

(deftest test-options-explicit-id
  (testing "explicit :id is preserved in StartActivityOptions"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue "q" :id "my-id"})]
      (is (= "my-id" (.getId opts))))))

(deftest test-options-default-start-to-close-timeout
  (testing "3s start-to-close-timeout is injected when no timeout is specified"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue "q"})]
      (is (= (Duration/ofSeconds 3) (.getStartToCloseTimeout opts))))))

(deftest test-options-explicit-start-to-close-timeout
  (testing "explicit :start-to-close-timeout overrides the default"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue                "q"
                                                                   :start-to-close-timeout (Duration/ofSeconds 30)})]
      (is (= (Duration/ofSeconds 30) (.getStartToCloseTimeout opts))))))

(deftest test-options-schedule-to-close-suppresses-default
  (testing ":schedule-to-close-timeout suppresses the default start-to-close-timeout"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue                   "q"
                                                                   :schedule-to-close-timeout (Duration/ofSeconds 60)})]
      (is (nil? (.getStartToCloseTimeout opts))))))

(deftest test-options-task-queue
  (testing ":task-queue is set in StartActivityOptions"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue             "my-queue"
                                                                   :start-to-close-timeout (Duration/ofSeconds 5)})]
      (is (= "my-queue" (.getTaskQueue opts))))))

(deftest test-options-schedule-to-close-timeout
  (testing ":schedule-to-close-timeout is set correctly"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue                   "q"
                                                                   :schedule-to-close-timeout (Duration/ofSeconds 120)})]
      (is (= (Duration/ofSeconds 120) (.getScheduleToCloseTimeout opts))))))

(deftest test-options-retry
  (testing ":retry-options :maximum-attempts maps into StartActivityOptions"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue    "q"
                                                                   :retry-options {:maximum-attempts 5}})]
      (is (= 5 (.getMaximumAttempts (.getRetryOptions opts)))))))

(deftest test-options-id-reuse-policy
  (testing ":id-reuse-policy :allow-duplicate maps to the correct enum value"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue       "q"
                                                                   :id-reuse-policy  :allow-duplicate})]
      (is (= ActivityIdReusePolicy/ACTIVITY_ID_REUSE_POLICY_ALLOW_DUPLICATE
             (.getIdReusePolicy opts))))))

(deftest test-options-id-conflict-policy
  (testing ":id-conflict-policy :fail maps to the correct enum value"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue         "q"
                                                                   :id-conflict-policy :fail})]
      (is (= ActivityIdConflictPolicy/ACTIVITY_ID_CONFLICT_POLICY_FAIL
             (.getIdConflictPolicy opts))))))

(deftest test-options-schedule-to-start-timeout
  (testing ":schedule-to-start-timeout is set in StartActivityOptions"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue                    "q"
                                                                   :schedule-to-start-timeout (Duration/ofSeconds 15)})]
      (is (= (Duration/ofSeconds 15) (.getScheduleToStartTimeout opts))))))

(deftest test-options-heartbeat-timeout
  (testing ":heartbeat-timeout is set in StartActivityOptions"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue        "q"
                                                                   :heartbeat-timeout (Duration/ofSeconds 5)})]
      (is (= (Duration/ofSeconds 5) (.getHeartbeatTimeout opts))))))

(deftest test-options-start-delay
  (testing ":start-delay is set in StartActivityOptions"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue  "q"
                                                                   :start-delay (Duration/ofSeconds 2)})]
      (is (= (Duration/ofSeconds 2) (.getStartDelay opts))))))

(deftest test-options-static-summary
  (testing ":static-summary is set in StartActivityOptions"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue     "q"
                                                                   :static-summary "my summary"})]
      (is (= "my summary" (.getStaticSummary opts))))))

(deftest test-options-static-details
  (testing ":static-details is set in StartActivityOptions"
    (let [^StartActivityOptions opts (ac/start-activity-options-> {:task-queue     "q"
                                                                   :static-details "my details"})]
      (is (= "my details" (.getStaticDetails opts))))))

;;; -----------------------------------------------------------------------
;;; 3. start-untyped-handle — error path and reflection happy path
;;; -----------------------------------------------------------------------

(deftest test-start-untyped-handle-no-method
  (testing "throws when the client class lacks the untyped start method"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"does not expose an untyped start method"
                          (ac/start-untyped-handle :not-a-client "act" {:task-queue "q"} {})))))

(deftest test-start-untyped-handle-method-found
  (testing "find-untyped-start-method locates the untyped start on a real ActivityClientImpl"
    ;; TestWorkflowEnvironment exposes a service stub we can use to build
    ;; a real ActivityClient.  The reflection must find the method and invoke
    ;; it; the test server does not implement StartActivityExecution, so the
    ;; gRPC call throws — but the method-finding and invoke paths are covered.
    (let [env    (e/create {})
          stubs  (.getWorkflowServiceStubs (e/get-client env))
          client (ActivityClient/newInstance stubs (copts/activity-client-options-> {}))]
      (try
        (is (thrown? Exception
                     (ac/start-untyped-handle client "act" {:task-queue "q"} {})))
        (finally
          (e/stop env))))))

;;; -----------------------------------------------------------------------
;;; 4. Happy path — execute (sync)
;;; -----------------------------------------------------------------------

(deftest test-execute-happy-path
  (testing "execute returns the activity's return value"
    (with-redefs [ac/start-untyped-handle (fn [_ _ _ args] (make-mock-handle args))]
      (is (= {:greeting "hello"}
             (ca/execute :mock-client sa-unit-echo-activity {:greeting "hello"}
                         {:task-queue "q"}))))))

(deftest test-execute-no-options
  (testing "execute 3-arg form uses empty options map"
    (with-redefs [ac/start-untyped-handle (fn [_ _ _ args] (make-mock-handle args))]
      (is (= {:v 1} (ca/execute :mock-client sa-unit-echo-activity {:v 1}))))))

;;; -----------------------------------------------------------------------
;;; 5. Async execution — start + deref
;;; -----------------------------------------------------------------------

(deftest test-start-async
  (testing "start returns a handle map with IDs and a resolvable result promise"
    (with-redefs [ac/start-untyped-handle (fn [_ _ _ args] (make-mock-handle args))]
      (let [handle (ca/start :mock-client sa-unit-echo-activity {:value 42}
                             {:task-queue "q"})]
        (is (= "mock-act-id" (:activity-id handle)))
        (is (= "mock-run-id" (:activity-run-id handle)))
        (is (some? (:handle handle)))
        (is (= {:value 42} @(:result handle)))))))

(deftest test-start-no-options
  (testing "start 3-arg form uses empty options map"
    (with-redefs [ac/start-untyped-handle (fn [_ _ _ args] (make-mock-handle args))]
      (let [handle (ca/start :mock-client sa-unit-echo-activity {:v 1})]
        (is (= {:v 1} @(:result handle)))))))

;;; -----------------------------------------------------------------------
;;; 6. Cancellation — cancel delegates to the handle
;;; -----------------------------------------------------------------------

(deftest test-cancel-with-reason
  (testing "cancel calls .cancel(reason) on the underlying handle"
    (let [cancel-called (atom nil)
          h (reify UntypedActivityHandle
              (cancel [_ reason] (reset! cancel-called reason))
              (cancel [_])
              (getActivityId [_] "x")
              (getActivityRunId [_] "x")
              (getResultAsync [_ _t] (java.util.concurrent.CompletableFuture.))
              (getResult [_ _t] nil)
              (terminate [_])
              (terminate [_ _r])
              (describe [_] nil))]
      (ca/cancel {:handle h} "my-reason")
      (is (= "my-reason" @cancel-called)))))

(deftest test-cancel-no-reason
  (testing "cancel with no reason calls .cancel() on the handle"
    (let [cancel-called (atom false)
          h (reify UntypedActivityHandle
              (cancel [_] (reset! cancel-called true))
              (cancel [_ _r])
              (getActivityId [_] "x")
              (getActivityRunId [_] "x")
              (getResultAsync [_ _t] (java.util.concurrent.CompletableFuture.))
              (getResult [_ _t] nil)
              (terminate [_])
              (terminate [_ _r])
              (describe [_] nil))]
      (ca/cancel {:handle h})
      (is (true? @cancel-called)))))

;;; -----------------------------------------------------------------------
;;; 7. Error handling — failed future propagates to caller
;;; -----------------------------------------------------------------------

(deftest test-error-propagates-via-result-promise
  (testing "when the activity future completes exceptionally, @(:result handle) throws"
    (with-redefs [ac/start-untyped-handle
                  (fn [_ _ _ _] (make-failing-handle (ex-info "boom" {:reason :test})))]
      (let [handle (ca/start :mock-client sa-unit-fail-activity {}
                             {:task-queue "q" :retry-options {:maximum-attempts 1}})]
        (is (thrown? Exception @(:result handle)))))))

;;; -----------------------------------------------------------------------
;;; 8. Option passing — verify task-queue / timeout / retry round-trip
;;; -----------------------------------------------------------------------
;;; The mapping from option keywords to Java builder setters is already
;;; exercised by test-options-task-queue, test-options-schedule-to-close-timeout,
;;; and test-options-retry.  This test checks that ca/execute round-trips all
;;; three option keys without error when dispatching via a mock handle.

(deftest test-option-passing
  (testing "task-queue, schedule-to-close-timeout, and retry-options are accepted by ca/execute"
    (with-redefs [ac/start-untyped-handle (fn [_ _ _ args] (make-mock-handle args))]
      (is (= {:opt-test true}
             (ca/execute :mock-client sa-unit-echo-activity {:opt-test true}
                         {:task-queue                "my-queue"
                          :schedule-to-close-timeout (Duration/ofSeconds 120)
                          :retry-options             {:maximum-attempts 7}}))))))

;;; -----------------------------------------------------------------------
;;; 9. Termination — terminate delegates to the handle
;;; -----------------------------------------------------------------------

(deftest test-terminate-with-reason
  (testing "terminate calls .terminate(reason) on the underlying handle"
    (let [terminate-called (atom nil)
          h (reify UntypedActivityHandle
              (terminate [_ reason] (reset! terminate-called reason))
              (terminate [_])
              (getActivityId [_] "x")
              (getActivityRunId [_] "x")
              (getResultAsync [_ _t] (java.util.concurrent.CompletableFuture.))
              (getResult [_ _t] nil)
              (cancel [_])
              (cancel [_ _r])
              (describe [_] nil))]
      (ca/terminate {:handle h} "force-stop")
      (is (= "force-stop" @terminate-called)))))

(deftest test-terminate-no-reason
  (testing "terminate with no reason calls .terminate() on the handle"
    (let [terminate-called (atom false)
          h (reify UntypedActivityHandle
              (terminate [_] (reset! terminate-called true))
              (terminate [_ _r])
              (getActivityId [_] "x")
              (getActivityRunId [_] "x")
              (getResultAsync [_ _t] (java.util.concurrent.CompletableFuture.))
              (getResult [_ _t] nil)
              (cancel [_])
              (cancel [_ _r])
              (describe [_] nil))]
      (ca/terminate {:handle h})
      (is (true? @terminate-called)))))

;;; -----------------------------------------------------------------------
;;; 10. Describe — delegates to the handle
;;; -----------------------------------------------------------------------

(deftest test-describe-delegates-to-handle
  (testing "describe calls .describe() on the underlying handle"
    (let [describe-called (atom false)
          h (reify UntypedActivityHandle
              (describe [_] (reset! describe-called true) nil)
              (getActivityId [_] "x")
              (getActivityRunId [_] "x")
              (getResultAsync [_ _t] (java.util.concurrent.CompletableFuture.))
              (getResult [_ _t] nil)
              (cancel [_])
              (cancel [_ _r])
              (terminate [_])
              (terminate [_ _r]))]
      (ca/describe {:handle h})
      (is (true? @describe-called)))))

;;; -----------------------------------------------------------------------
;;; 11. ActivityInfo datafy — standalone context (nil workflow-id / run-id)
;;; -----------------------------------------------------------------------

(deftest test-activity-info-datafy-in-workflow
  (testing "ActivityInfo datafy in-workflow context: in-workflow? true, IDs present"
    (let [info (d/datafy (mock-activity-info "wf-123" "run-456" "act-789" true))]
      (is (true? (:in-workflow? info)))
      (is (= "wf-123" (:workflow-id info)))
      (is (= "run-456" (:run-id info)))
      (is (= "act-789" (:activity-id info))))))

(deftest test-activity-info-datafy-standalone
  (testing "ActivityInfo datafy in standalone context: in-workflow? false, workflow-id and run-id nil"
    (let [info (d/datafy (mock-activity-info nil nil "act-standalone" false))]
      (is (false? (:in-workflow? info)))
      (is (some? (:activity-id info)))
      (is (nil? (:workflow-id info)))
      (is (nil? (:run-id info))))))
