;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.test.manual-dispatch
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.testing.env :as e]
            [temporal.workflow :refer [defworkflow]])
  (:import [java.time Duration]))

;; do not use the shared fixture, since we want to control the env creation

;;-----------------------------------------------------------------------------
;; Data
;;-----------------------------------------------------------------------------
(defonce state (atom {}))

(def task-queue ::default)

;;-----------------------------------------------------------------------------
;; Utilities
;;-----------------------------------------------------------------------------
(defn get-client []
  (get @state :client))

(defn create-workflow [workflow]
  (c/create-workflow (get-client) workflow {:task-queue task-queue :workflow-execution-timeout (Duration/ofSeconds 1) :retry-options {:maximum-attempts 1}}))

(defn invoke [workflow]
  (let [i (create-workflow workflow)]
    (c/start i {})
    @(c/get-result i)))

;;-----------------------------------------------------------------------------
;; Workflows
;;-----------------------------------------------------------------------------

(defworkflow explicitly-registered-workflow
  [ctx {:keys [args]}]
  (log/info "registered-workflow:" args)
  :ok)

(defworkflow explicitly-skipped-workflow
  [ctx {:keys [args]}]
  (log/info "skipped-workflow:" args)
  :ok)

;;-----------------------------------------------------------------------------
;; Fixtures
;;-----------------------------------------------------------------------------
(defn create-service []
  (let [{:keys [client] :as env} (e/create {:task-queue task-queue
                                            :dispatch {:workflows [explicitly-registered-workflow]}})] ;; note that we intentionally omit explicitly-skipped-workflow
    (swap! state assoc
           :env env
           :client client)))

(defn destroy-service []
  (swap! state
         (fn [{:keys [env] :as s}]
           (e/stop env)
           (dissoc s :env :client))))

(defn wrap-service [test-fn]
  (create-service)
  (test-fn)
  (destroy-service))

(use-fixtures :once wrap-service)

;;-----------------------------------------------------------------------------
;; Tests
;;-----------------------------------------------------------------------------

(deftest the-test
  (testing "Verifies that we can invoke our explicitly registered workflow"
    (is (-> (invoke explicitly-registered-workflow) (= :ok))))
  (testing "Verifies that we can NOT invoke our explicitly skipped workflow"
    (is (thrown? java.util.concurrent.ExecutionException
                 (invoke explicitly-skipped-workflow)))))
