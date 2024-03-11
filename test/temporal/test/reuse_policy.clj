;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.reuse-policy
  (:require [clojure.test :refer :all]
            [slingshot.slingshot :refer [try+ throw+]]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.side-effect :as s]
            [temporal.exceptions :as e]
            [temporal.test.utils :as t])
  (:import [io.temporal.client WorkflowExecutionAlreadyStarted]))

(use-fixtures :once t/wrap-service)

(defworkflow reuse-workflow
  [args]
  (log/info "reuse-workflow:" args)
  (case args
    :sleep (w/await (constantly false))
    :crash (throw+ {:type ::synthetic-crash ::e/non-retriable? true})
    :exit  (s/gen-uuid)))

(defn invoke [{:keys [policy wid args] :or {wid ::wid args :exit policy :allow-duplicate}}]
  (let [wf (c/create-workflow (t/get-client) reuse-workflow {:task-queue t/task-queue
                                                             :workflow-id wid
                                                             :workflow-id-reuse-policy policy})]
    (c/start wf args)
    @(c/get-result wf)))

(deftest the-test
  (testing "Verifies that :allow-duplicate policy works"
    (dotimes [_ 2]
      (is (some? (invoke {:wid ::allow-duplicate :policy :allow-duplicate})))))
  (testing "Verifies that :allow-duplicate-failed-only policy works"
    (try+
     (invoke {:wid ::allow-duplicate-failed-only :args :crash})
     (catch [:type ::synthetic-crash] _
       :ok))
    (is (some? (invoke {:wid ::allow-duplicate-failed-only :policy :allow-duplicate-failed-only}))))
  (testing "Verifies that :reject-duplicate policy works"
    (let [result (invoke {:wid ::reject-duplicate :policy :reject-duplicate})]
      (is (thrown? WorkflowExecutionAlreadyStarted (invoke {:wid ::reject-duplicate :policy :reject-duplicate})))
      (let [wf2 (c/create-workflow (t/get-client) ::reject-duplicate)]
        (is (= result @(c/get-result wf2))))))
  (testing "Verifies that :terminate-if-running policy works"
    (let [wf (c/create-workflow (t/get-client) reuse-workflow {:task-queue t/task-queue :workflow-id ::terminate-if-running})]
      (c/start wf :sleep)
      (is (some? (invoke {:wid ::terminate-if-running :policy :terminate-if-running})))))
  (testing "Verifies that a bogus reuse policy throws"
    (is (thrown? IllegalStateException (invoke {:wid ::bogus :policy :bogus})))))
