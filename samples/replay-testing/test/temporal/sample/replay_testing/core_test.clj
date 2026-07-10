(ns temporal.sample.replay-testing.core-test
  "Demonstrates Temporal's capture-then-replay testing pattern.

  The tests here show three progressions:

  1. Run the workflow and replay its live history — the basic replay check.
  2. Capture history to a file fixture and replay from that file — the CI-friendly
     pattern where you commit the fixture and skip re-running the workflow.
  3. Detect non-determinism — change the workflow's activity order and confirm
     that replay throws, catching the breaking change before it reaches production."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.testing.env :as env]
            [temporal.testing.history :as history]
            [temporal.testing.replayer :as replayer]
            [temporal.sample.replay-testing.core
             :refer [order-workflow validate-order charge-payment workflow-version]])
  (:import [java.time Duration]))

;; ---------------------------------------------------------------------------
;; Test infrastructure
;; ---------------------------------------------------------------------------

(def ^:private task-queue "order-replay-test-queue")

(def ^:private dispatch
  {:workflows  [order-workflow]
   :activities [validate-order charge-payment]})

(defonce ^:private test-state (atom {}))

(defn- get-client [] (:client @test-state))

(defn- wrap-service [test-fn]
  (reset! workflow-version :v1)
  (let [test-env (env/create)
        client   (env/get-client test-env)
        _        (env/start test-env {:task-queue task-queue :dispatch dispatch})]
    (swap! test-state assoc :env test-env :client client)
    (try
      (test-fn)
      (finally
        (env/synchronized-stop test-env)
        (reset! test-state {})))))

(use-fixtures :once wrap-service)

(defn- run-order! [order-id]
  (let [wf (c/create-workflow (get-client) order-workflow
                              {:task-queue                  task-queue
                               :workflow-id                 order-id
                               :dispatch                    dispatch
                               :workflow-execution-timeout  (Duration/ofSeconds 15)
                               :retry-options               {:maximum-attempts 1}})]
    (c/start wf {:order-id order-id})
    @(c/get-result wf)
    wf))

;; ---------------------------------------------------------------------------
;; Tests
;; ---------------------------------------------------------------------------

(deftest replay-live-history-test
  (testing "A completed workflow replays cleanly against the same code"
    (reset! workflow-version :v1)
    (run-order! "ord-live-1")
    (let [h (history/fetch (get-client) "ord-live-1")]
      (is (nil? (replayer/replay-history h {:dispatch dispatch}))))))

(deftest replay-from-fixture-test
  (testing "Capture history to file, then replay from file"
    ;; Step 1: run the workflow and capture its history.
    (reset! workflow-version :v1)
    (run-order! "ord-capture-1")
    (let [h       (history/fetch (get-client) "ord-capture-1")
          fixture "test/resources/histories/order.json"
          _       (log/info "Saving history to" fixture)
          _       (spit fixture (history/->json h))]
      ;; Step 2: load from file and replay — the pattern you commit to CI.
      (is (nil? (replayer/replay-history (history/from-file fixture) {:dispatch dispatch}))))))

(deftest replay-from-precaptured-fixture-test
  (testing "Pre-captured order.json fixture replays cleanly"
    ;; This test requires no live workflow run; it replays the committed fixture.
    ;; Run `lein test` once to generate and commit the file, then this test
    ;; passes in CI without a running Temporal server.
    (let [h (history/from-resource "histories/order.json")]
      (is (nil? (replayer/replay-history h {:dispatch dispatch}))))))

(deftest replay-detects-nondeterminism-test
  (testing "Non-determinism is detected when the workflow's activity order changes"
    ;; Capture a v1 history (validate-order → charge-payment).
    (reset! workflow-version :v1)
    (run-order! "ord-nondeterminism-1")
    (let [h (history/fetch (get-client) "ord-nondeterminism-1")]
      ;; Simulate a breaking code change: v2 reverses the activity order.
      ;; In a real project this would be a code change pushed to a branch.
      (reset! workflow-version :v2)
      (is (thrown? clojure.lang.ExceptionInfo
                   (replayer/replay-history h {:dispatch dispatch}))))))

(deftest replay-all-test
  (testing "replay-all returns total count and empty failures for valid histories"
    (reset! workflow-version :v1)
    (run-order! "ord-batch-1")
    (run-order! "ord-batch-2")
    (let [h1 (history/fetch (get-client) "ord-batch-1")
          h2 (history/fetch (get-client) "ord-batch-2")]
      (with-open [r (replayer/create {:dispatch dispatch})]
        (let [{:keys [total failures]} (replayer/replay-all r [h1 h2])]
          (is (= 2 total))
          (is (empty? failures)))))))
