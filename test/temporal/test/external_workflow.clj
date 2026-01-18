;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.external-workflow
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.signals :as s]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.test.utils :as t])
  (:import [io.temporal.failure CanceledFailure]))

(use-fixtures :once t/wrap-service)

(def signal-name ::signal)

;; Target workflow that waits for signals
(defworkflow target-workflow
  [args]
  (log/info "target-workflow:" args)
  (let [signals (s/create-signal-chan)]
    (s/<! signals signal-name)))

;; Controller workflow that uses external-workflow to signal another workflow
(defworkflow signal-controller-workflow
  [{:keys [target-id msg]}]
  (log/info "signal-controller-workflow:" target-id msg)
  (let [target (w/external-workflow target-id)]
    (w/signal-external target signal-name msg)
    :signaled))

(deftest signal-external-test
  (testing "Verifies that we can send signals to external workflows using signal-external"
    (let [target-wf (c/create-workflow (t/get-client) target-workflow {:task-queue t/task-queue :workflow-id "signal-target"})
          controller-wf (t/create-workflow signal-controller-workflow)]
      (c/start target-wf nil)
      (c/start controller-wf {:target-id "signal-target" :msg "Hello from controller"})
      (is (= @(c/get-result target-wf) "Hello from controller"))
      (is (= @(c/get-result controller-wf) :signaled)))))

;; Target workflow that waits indefinitely (for cancellation test)
(defworkflow cancellable-workflow
  [args]
  (log/info "cancellable-workflow:" args)
  (let [signals (s/create-signal-chan)]
    ;; Wait indefinitely for a signal (will be cancelled before it arrives)
    (s/<! signals signal-name)))

;; Controller workflow that cancels another workflow
(defworkflow cancel-controller-workflow
  [{:keys [target-id]}]
  (log/info "cancel-controller-workflow:" target-id)
  (let [target (w/external-workflow target-id)]
    (w/cancel-external target)
    :cancelled))

(deftest cancel-external-test
  (testing "Verifies that we can cancel external workflows using cancel-external"
    (let [target-wf (c/create-workflow (t/get-client) cancellable-workflow {:task-queue t/task-queue :workflow-id "cancel-target"})
          controller-wf (t/create-workflow cancel-controller-workflow)]
      (c/start target-wf nil)
      ;; Give the target workflow a moment to start
      (Thread/sleep 100)
      (c/start controller-wf {:target-id "cancel-target"})
      (is (= @(c/get-result controller-wf) :cancelled))
      ;; The target workflow should throw CanceledFailure
      (is (thrown? java.util.concurrent.ExecutionException
                   @(c/get-result target-wf))))))

;; Workflow that gets execution info
(defworkflow get-execution-workflow
  [{:keys [target-id]}]
  (log/info "get-execution-workflow:" target-id)
  (let [target (w/external-workflow target-id)
        execution (w/get-execution target)]
    {:workflow-id (:workflow-id execution)
     :has-run-id (some? (:run-id execution))}))

;; Target workflow for get-execution test - just waits for a signal then returns
(defworkflow execution-target-workflow
  [args]
  (log/info "execution-target-workflow:" args)
  (let [signals (s/create-signal-chan)]
    (s/<! signals signal-name)))

(deftest get-execution-test
  (testing "Verifies that we can get execution info from external workflow handles"
    (let [target-wf (c/create-workflow (t/get-client) execution-target-workflow {:task-queue t/task-queue :workflow-id "execution-target"})
          getter-wf (t/create-workflow get-execution-workflow)]
      (c/start target-wf nil)
      (c/start getter-wf {:target-id "execution-target"})
      (let [result @(c/get-result getter-wf)]
        (is (= (:workflow-id result) "execution-target"))
        (is (true? (:has-run-id result))))
      ;; Signal the target to complete it
      (c/>! target-wf signal-name :done))))

;; Workflow that creates external-workflow with run-id
(defworkflow external-with-run-id-workflow
  [{:keys [target-id target-run-id msg]}]
  (log/info "external-with-run-id-workflow:" target-id target-run-id msg)
  (let [target (w/external-workflow target-id target-run-id)]
    (w/signal-external target signal-name msg)
    :signaled))

(deftest external-workflow-with-run-id-test
  (testing "Verifies that we can create external workflow handles with specific run-id"
    (let [target-wf (c/create-workflow (t/get-client) target-workflow {:task-queue t/task-queue :workflow-id "run-id-target"})
          _ (c/start target-wf nil)
          ;; Get the run-id from the target workflow's stub
          target-run-id (.getRunId (.getExecution (:stub target-wf)))
          controller-wf (t/create-workflow external-with-run-id-workflow)]
      (c/start controller-wf {:target-id "run-id-target" :target-run-id target-run-id :msg "With run-id"})
      (is (= @(c/get-result target-wf) "With run-id"))
      (is (= @(c/get-result controller-wf) :signaled)))))
