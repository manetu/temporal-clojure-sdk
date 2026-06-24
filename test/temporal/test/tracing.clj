;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.tracing
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.testing.env :as e]
            [temporal.workflow :refer [defworkflow]])
  (:import [io.opentracing.mock MockTracer]
           [io.temporal.opentracing OpenTracingClientInterceptor OpenTracingOptions OpenTracingWorkerInterceptor]))

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

(defn get-tracer []
  (get @state :tracer))

(defn create-workflow [workflow]
  (c/create-workflow (get-client) workflow {:task-queue task-queue}))

(defn invoke [workflow]
  (let [i (create-workflow workflow)]
    (c/start i {})
    @(c/get-result i)))

;;-----------------------------------------------------------------------------
;; Workflows
;;-----------------------------------------------------------------------------

(defworkflow traced-workflow
  [args]
  (log/info "traced-workflow:" args)
  :ok)

;;-----------------------------------------------------------------------------
;; Fixtures
;;-----------------------------------------------------------------------------
(defn create-service []
  (let [tracer (MockTracer.)
        opts   (-> (OpenTracingOptions/newBuilder)
                   (.setTracer tracer)
                   (.build))
        env    (e/create {:workflow-client-options {:interceptors [(OpenTracingClientInterceptor. opts)]}
                          :worker-factory-options {:worker-interceptors [(OpenTracingWorkerInterceptor. opts)]}})
        client (e/get-client env)]
    (e/start env {:task-queue task-queue})
    (swap! state assoc
           :env env
           :client client
           :tracer tracer)))

(defn destroy-service []
  (swap! state
         (fn [{:keys [env] :as s}]
           (e/stop env)
           (dissoc s :env :client :tracer))))

(defn wrap-service [test-fn]
  (create-service)
  (test-fn)
  (destroy-service))

(use-fixtures :once wrap-service)

;;-----------------------------------------------------------------------------
;; Tests
;;-----------------------------------------------------------------------------

(deftest the-test
  (testing "Verifies that we can invoke our traced workflow"
    (is (-> (invoke traced-workflow) (= :ok))))
  (testing "Verifies that tracing spans are captured"
    (let [spans    (.finishedSpans (get-tracer))
          op-names (->> spans (map #(.operationName %)) set)]
      (is (contains? op-names "StartWorkflow:traced-workflow"))
      (is (contains? op-names "RunWorkflow:traced-workflow")))))
