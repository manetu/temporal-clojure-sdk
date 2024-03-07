;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.versioning
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [mockery.core :refer [with-mock]]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(def mailbox (atom nil))

;; Only serves to generate events in our history
(defactivity versioned-activity
  [ctx args]
  (reset! mailbox args)
  args)

(defn workflow-v1
  []
  (log/info "versioned-workflow v1")
  @(a/invoke versioned-activity :foo)
  @(a/invoke versioned-activity :v1))

(defn workflow-v2
  []
  (log/info "versioned-workflow v2")
  @(a/invoke versioned-activity :foo)
  (let [version (w/get-version ::test w/default-version 1)]
    (cond
      (= version w/default-version)  @(a/invoke versioned-activity :v1)
      (= version 1)                  @(a/local-invoke versioned-activity :v2))))

(defworkflow versioned-workflow
  [args]
  (workflow-v1))

(deftest the-test
  (let [client (t/get-client)
        worker (t/get-worker)
        wf (c/create-workflow client versioned-workflow {:task-queue t/task-queue :workflow-id "test-1"})]
    (testing "Invoke our v1 workflow"
      (c/start wf {})
      (is (= @(c/get-result wf) :v1))
      (is (= @mailbox :v1)))
    (with-mock _
      {:target ::workflow-v1
       :return workflow-v2}                               ;; emulates a code update by dynamically substituting v2 for v1
      (testing "Replay the workflow after upgrading the code"
        (reset! mailbox :slug)
        (let [history (.fetchHistory client "test-1")]
          (.replayWorkflowExecution worker history))
        (is (= @mailbox :slug)))                            ;; activity is not re-executed in replay, so the :slug should remain
      (testing "Invoke our workflow fresh and verify that it takes the v2 path"
        (reset! mailbox nil)
        (let [wf2 (c/create-workflow client versioned-workflow {:task-queue t/task-queue :workflow-id "test-2"})]
          (c/start wf2 {})
          (is (= @(c/get-result wf2) :v2))
          (is (= @mailbox :v2)))))))
