;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.workflow-update
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.signals :as s]
            [temporal.test.utils :as t]
            [temporal.internal.workflow :as iw])
  (:import [java.time Duration]))

(use-fixtures :once t/wrap-service)

(defworkflow update-workflow
  [args]
  (let [state (atom {:counter 0})
        signals (s/create-signal-chan)]
    (w/register-update-handler!
     (fn [update-type args]
       (log/info "update-handler: type=" update-type "args=" args)
       (case update-type
         :increment (do
                      (swap! state update :counter + (:amount args))
                      @state)
         :get-state @state
         nil)))
    ;; Wait for done signal
    (s/<! signals ::done)
    @state))

(defworkflow validated-update-workflow
  [args]
  (let [state (atom {:counter 0})
        signals (s/create-signal-chan)]
    (w/register-update-handler!
     (fn [update-type args]
       (log/info "validated-update-handler: type=" update-type "args=" args)
       (case update-type
         :increment (do
                      (swap! state update :counter + (:amount args))
                      @state)
         nil))
     {:validator (fn [update-type args]
                   (log/info "validator: type=" update-type "args=" args)
                   (when (and (= update-type :increment)
                              (neg? (:amount args)))
                     (throw (IllegalArgumentException. "amount must be positive"))))})
    ;; Wait for done signal
    (s/<! signals ::done)
    @state))

(deftest update-test
  (testing "Verifies that updates can modify workflow state and return values"
    (let [workflow (t/create-workflow update-workflow)]
      (c/start workflow {})
      ;; Send an update immediately after start - no sleep needed
      (let [result (c/update workflow :increment {:amount 5})]
        (is (= {:counter 5} result)))
      ;; Send another update
      (let [result (c/update workflow :increment {:amount 3})]
        (is (= {:counter 8} result)))
      ;; Signal workflow to complete
      (c/>! workflow ::done {})
      ;; Verify final workflow result
      (is (= {:counter 8} @(c/get-result workflow))))))

(deftest validated-update-test
  (testing "Verifies that update validators work correctly"
    (let [workflow (t/create-workflow validated-update-workflow)]
      (c/start workflow {})
      ;; Valid update should succeed
      (let [result (c/update workflow :increment {:amount 5})]
        (is (= {:counter 5} result)))
      ;; Invalid update should fail validation
      (is (thrown? Exception (c/update workflow :increment {:amount -3})))
      ;; State should be unchanged after failed validation
      (let [result (c/update workflow :increment {:amount 2})]
        (is (= {:counter 7} result)))
      ;; Signal workflow to complete
      (c/>! workflow ::done {})
      ;; Verify final workflow result
      (is (= {:counter 7} @(c/get-result workflow))))))

(deftest start-update-test
  (testing "Verifies that start-update returns a handle for async updates"
    (let [workflow (t/create-workflow update-workflow)]
      (c/start workflow {})
      ;; Start an async update with custom options
      (let [custom-id (str "custom-update-" (java.util.UUID/randomUUID))
            handle (c/start-update workflow :increment {:amount 10}
                                   {:update-id custom-id
                                    :wait-for-stage :completed})]
        ;; Handle should have the custom ID we specified
        (is (= custom-id (:id handle)))
        (is (some? (:result handle)))
        ;; Wait for the result
        (is (= {:counter 10} @(:result handle))))
      ;; Signal workflow to complete
      (c/>! workflow ::done {})
      (is (= {:counter 10} @(c/get-result workflow))))))

(deftest get-update-handle-test
  (testing "Verifies that get-update-handle retrieves an existing update handle"
    (let [workflow (t/create-workflow update-workflow)]
      (c/start workflow {})
      ;; Start an update with :admitted stage (tests all three stage values)
      (let [handle1 (c/start-update workflow :increment {:amount 5} {:wait-for-stage :accepted})
            update-id (:id handle1)
            ;; Retrieve the same update handle
            handle2 (c/get-update-handle workflow update-id)]
        ;; Both handles should have the same ID
        (is (= update-id (:id handle2)))
        ;; Both should resolve to the same result
        (is (= {:counter 5} @(:result handle1)))
        (is (= {:counter 5} @(:result handle2))))
      ;; Signal workflow to complete
      (c/>! workflow ::done {})
      (is (= {:counter 5} @(c/get-result workflow))))))

(defworkflow update-with-start-workflow
  [{:keys [initial-value] :or {initial-value 0}}]
  (let [state (atom {:counter initial-value})
        signals (s/create-signal-chan)]
    (w/register-update-handler!
     (fn [update-type args]
       (log/info "update-with-start-handler: type=" update-type "args=" args)
       (case update-type
         :increment (do
                      (swap! state update :counter + (:amount args))
                      @state)
         :get-state @state
         nil)))
    ;; Wait for done signal
    (s/<! signals ::done)
    @state))

(deftest invalid-conflict-policy-test
  (testing "Verifies that invalid workflow-id-conflict-policy throws an error"
    (is (thrown-with-msg? IllegalArgumentException #"Unknown workflow-id-conflict-policy"
                          (iw/workflow-id-conflict-policy-> :invalid-policy))))
  (testing "Verifies that invalid workflow-id-reuse-policy throws an error"
    (is (thrown-with-msg? IllegalArgumentException #"Unknown workflow-id-reuse-policy"
                          (iw/workflow-id-reuse-policy-> :invalid-policy)))))

(deftest update-with-start-test
  (testing "Verifies that update-with-start uses an existing workflow"
    ;; update-with-start requires workflow-id-conflict-policy to be set
    ;; This tests the "use existing" path - start workflow first, then send update-with-start
    (let [workflow-id (str (java.util.UUID/randomUUID))
          workflow (c/create-workflow (t/get-client) update-with-start-workflow
                                      {:task-queue t/task-queue
                                       :workflow-id workflow-id
                                       :workflow-execution-timeout (Duration/ofSeconds 10)
                                       :retry-options {:maximum-attempts 1}
                                       :workflow-id-conflict-policy :use-existing})]
      ;; Start the workflow first using regular start
      (c/start workflow {:initial-value 100})
      ;; Send first update using regular update to ensure handler is registered
      (let [result (c/update workflow :increment {:amount 3})]
        (is (= {:counter 103} result)))
      ;; Now test update-with-start on the already running workflow with custom options
      ;; Create a new stub with the same workflow-id (simulates what a client would do)
      (let [workflow2 (c/create-workflow (t/get-client) update-with-start-workflow
                                         {:task-queue t/task-queue
                                          :workflow-id workflow-id
                                          :workflow-execution-timeout (Duration/ofSeconds 10)
                                          :retry-options {:maximum-attempts 1}
                                          :workflow-id-conflict-policy :use-existing})
            custom-update-id (str "update-with-start-" (java.util.UUID/randomUUID))
            handle (c/update-with-start workflow2 :increment {:amount 7} {:initial-value 0}
                                        {:update-id custom-update-id
                                         :wait-for-stage :completed})]
        ;; Verify the custom update-id was used
        (is (= custom-update-id (:id handle)))
        ;; The workflow was already started with initial-value 100, so this uses existing
        ;; State should be 103 + 7 = 110
        (is (= {:counter 110} @(:result handle))))
      ;; Signal workflow to complete
      (c/>! workflow ::done {})
      (is (= {:counter 110} @(c/get-result workflow))))))
