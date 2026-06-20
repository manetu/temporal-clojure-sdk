;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.replay-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [mockery.core :refer [with-mock]]
            [taoensso.timbre :as log]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.client.core :as c]
            [temporal.testing.history :as history]
            [temporal.testing.replayer :as replayer]
            [temporal.test.utils :as t]
            [temporal.workflow :refer [defworkflow]]))

(use-fixtures :once t/wrap-service)

;;; Deterministic workflow

(defactivity replay-greet-activity
  [_ctx {:keys [name] :as args}]
  (log/info "replay-greet-activity:" args)
  (str "Hi, " name))

(defworkflow replay-greeter-workflow
  [args]
  (log/info "replay-greeter-workflow:" args)
  @(a/invoke replay-greet-activity args))

(defn run-and-capture-history []
  (let [client   (t/get-client)
        wf-id    (str "replay-test-" (java.util.UUID/randomUUID))
        workflow (c/create-workflow client replay-greeter-workflow
                                    {:task-queue t/task-queue
                                     :workflow-id wf-id})]
    (c/start workflow {:name "Alice"})
    @(c/get-result workflow)
    (history/fetch client wf-id)))

;;; Non-deterministic workflow (for failure testing)

(defactivity replay-nd-activity
  [_ctx args]
  (log/info "replay-nd-activity:" args)
  args)

(defn nd-v1 [args]
  @(a/invoke replay-nd-activity args))

(defn nd-v2 [args]
  @(a/invoke replay-nd-activity args)
  @(a/invoke replay-nd-activity args))

(defworkflow replay-nd-workflow
  [args]
  (log/info "replay-nd-workflow:" args)
  (nd-v1 args))

(defn run-nd-history []
  (let [client   (t/get-client)
        wf-id    (str "replay-nd-" (java.util.UUID/randomUUID))
        workflow (c/create-workflow client replay-nd-workflow
                                    {:task-queue t/task-queue
                                     :workflow-id wf-id})]
    (c/start workflow {:step 1})
    @(c/get-result workflow)
    (history/fetch client wf-id)))

;;; Tests

(deftest replay-one-shot-test
  (testing "replay-history replays a valid completed workflow without throwing"
    (let [h (run-and-capture-history)]
      (is (nil? (replayer/replay-history h))))))

(deftest replay-with-open-test
  (testing "create returns a Closeable that works with with-open"
    (let [h (run-and-capture-history)]
      (with-open [r (replayer/create)]
        (is (nil? (replayer/replay r h)))))))

(deftest replay-non-determinism-test
  (testing "replay throws ex-info with :workflow-id when non-determinism is detected"
    (let [h (run-nd-history)]
      (with-mock _
        {:target ::nd-v1
         :return nd-v2}
        (let [err (try
                    (replayer/replay-history h)
                    nil
                    (catch clojure.lang.ExceptionInfo e e))]
          (is (some? err))
          (is (= (.. h getWorkflowExecution getWorkflowId)
                 (:workflow-id (ex-data err)))))))))

(deftest replay-all-test
  (testing "replay-all returns total and empty failures for valid histories"
    (let [h1 (run-and-capture-history)
          h2 (run-and-capture-history)]
      (with-open [r (replayer/create)]
        (let [{:keys [total failures]} (replayer/replay-all r [h1 h2])]
          (is (= 2 total))
          (is (empty? failures)))))))

(deftest replay-all-partial-failure-test
  (testing "replay-all collects all failures when fail-fast? false"
    (let [h-good (run-and-capture-history)
          h-bad1 (run-nd-history)
          h-bad2 (run-nd-history)]
      (with-mock _
        {:target ::nd-v1
         :return nd-v2}
        (with-open [r (replayer/create)]
          (let [{:keys [total failures]} (replayer/replay-all r [h-good h-bad1 h-bad2] {:fail-fast? false})]
            (is (= 3 total))
            (is (= 2 (count failures)))
            (is (every? :workflow-id failures))))))))

(deftest replay-all-fail-fast-test
  (testing "replay-all throws on first failure when fail-fast? true"
    (let [h-bad1 (run-nd-history)
          h-bad2 (run-nd-history)
          h-good (run-and-capture-history)]
      (with-mock _
        {:target ::nd-v1
         :return nd-v2}
        (with-open [r (replayer/create)]
          (is (thrown? clojure.lang.ExceptionInfo
                       (replayer/replay-all r [h-bad1 h-bad2 h-good] {:fail-fast? true}))))))))

(deftest replay-close-test
  (testing "close and synchronized-stop do not throw"
    (let [r (replayer/create)]
      (replayer/close r))
    (let [r (replayer/create)]
      (replayer/synchronized-stop r))))
