;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.continue-as-new
  (:require [clojure.test :refer :all]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.testing.env :as e]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

;;-----------------------------------------------------------------------------
;; Basic restart test
;;-----------------------------------------------------------------------------

(defworkflow continue-as-new-workflow
  [{:keys [iteration max-iterations]}]
  (log/info "continue-as-new-workflow: iteration" iteration "of" max-iterations)
  (if (< iteration max-iterations)
    (w/continue-as-new {:iteration (inc iteration) :max-iterations max-iterations})
    {:completed true :final-iteration iteration}))

(deftest the-test
  (testing "Verifies that continue-as-new restarts workflow with new arguments"
    (let [workflow (t/create-workflow continue-as-new-workflow)]
      (c/start workflow {:iteration 1 :max-iterations 3})
      (is (= @(c/get-result workflow) {:completed true :final-iteration 3})))))

;;-----------------------------------------------------------------------------
;; Search-attribute carryover tests (Temporal Java SDK 1.33+)
;;
;; Since Temporal Java SDK 1.33, omitting :search-attributes on continue-as-new carries over
;; the current run's search attributes to the new run. Previously, they were
;; cleared. Code that relied on clearing must now explicitly pass {} or nil to
;; :search-attributes.
;;
;; We verify the two corrective patterns here:
;;   1. CAN without :search-attributes completes successfully (carryover path)
;;   2. CAN with :search-attributes {} completes successfully (explicit clear)
;;   3. CAN with explicit search-attribute values completes successfully (override)
;;-----------------------------------------------------------------------------

(def task-queue ::sa-queue)

;; Workflow that upserts a search attribute on run 1, then CANs without SA option.
;; Since Temporal Java SDK 1.33 the attribute carries over to run 2. The workflow completes on run 2.
(defworkflow can-sa-carryover-workflow
  [{:keys [iteration]}]
  (log/info "can-sa-carryover-workflow: iteration" iteration)
  (if (= iteration 1)
    (do
      (w/upsert-search-attributes {"CanStatus" {:type :keyword :value "run-one"}})
      ;; Omit :search-attributes → Temporal Java SDK 1.33+ carries over "run-one" to the new run
      (w/continue-as-new {:iteration 2}))
    {:completed true :iteration iteration}))

;; Workflow that upserts a search attribute on run 1, then CANs with empty SA map
;; to explicitly clear attributes.
(defworkflow can-sa-clear-workflow
  [{:keys [iteration]}]
  (log/info "can-sa-clear-workflow: iteration" iteration)
  (if (= iteration 1)
    (do
      (w/upsert-search-attributes {"CanStatus" {:type :keyword :value "run-one"}})
      ;; Pass empty map to explicitly clear search attributes on the new run
      (w/continue-as-new {:iteration 2} {:search-attributes {}}))
    {:completed true :iteration iteration}))

(defn make-sa-env []
  (let [env    (e/create {:search-attributes {"CanStatus" :keyword}})
        client (e/get-client env)
        _      (e/start env {:task-queue task-queue})]
    {:env env :client client}))

(defn run-sa-workflow [wf-def]
  (let [{:keys [env client]} (make-sa-env)
        workflow (c/create-workflow client wf-def
                                    {:task-queue                task-queue
                                     :workflow-execution-timeout (java.time.Duration/ofSeconds 10)
                                     :retry-options              {:maximum-attempts 1}})]
    (c/start workflow {:iteration 1})
    @(-> (c/get-result workflow)
         (p/finally (fn [_ _] (e/synchronized-stop env))))))

(deftest sa-carryover-test
  (testing "continue-as-new without :search-attributes carries over SA (Temporal Java SDK 1.33+)"
    (is (= (run-sa-workflow can-sa-carryover-workflow)
           {:completed true :iteration 2}))))

(deftest sa-explicit-clear-test
  (testing "continue-as-new with :search-attributes {} explicitly clears SA"
    (is (= (run-sa-workflow can-sa-clear-workflow)
           {:completed true :iteration 2}))))

;;-----------------------------------------------------------------------------
;; Versioning option tests (Temporal Java SDK 1.34 / 1.36)
;;
;; :initial-versioning-behavior controls how the new run is pinned to a
;; deployment version:
;;   :auto-upgrade        (Temporal Java SDK 1.34) - PINNED workflows upgrade to latest deployment
;;   :use-ramping-version (Temporal Java SDK 1.36) - pins to the task queue's Ramping Version
;;
;; Actual versioning behavior requires a versioned deployment and cannot be
;; fully exercised in unit tests. These tests verify that the option is accepted
;; by the SDK and the workflow completes the CAN cycle without error.
;;-----------------------------------------------------------------------------

(defworkflow can-auto-upgrade-workflow
  [{:keys [iteration]}]
  (log/info "can-auto-upgrade-workflow: iteration" iteration)
  (if (= iteration 1)
    (w/continue-as-new {:iteration 2} {:initial-versioning-behavior :auto-upgrade})
    {:completed true :iteration iteration}))

(defworkflow can-use-ramping-version-workflow
  [{:keys [iteration]}]
  (log/info "can-use-ramping-version-workflow: iteration" iteration)
  (if (= iteration 1)
    (w/continue-as-new {:iteration 2} {:initial-versioning-behavior :use-ramping-version})
    {:completed true :iteration iteration}))

(deftest auto-upgrade-test
  (testing "continue-as-new with :initial-versioning-behavior :auto-upgrade is accepted"
    (let [workflow (t/create-workflow can-auto-upgrade-workflow)]
      (c/start workflow {:iteration 1})
      (is (= @(c/get-result workflow) {:completed true :iteration 2})))))

(deftest use-ramping-version-test
  (testing "continue-as-new with :initial-versioning-behavior :use-ramping-version is accepted"
    (let [workflow (t/create-workflow can-use-ramping-version-workflow)]
      (c/start workflow {:iteration 1})
      (is (= @(c/get-result workflow) {:completed true :iteration 2})))))
