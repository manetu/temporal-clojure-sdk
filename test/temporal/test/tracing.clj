;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.tracing
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.testing.env :as e]
            [temporal.workflow :refer [defworkflow]])
  (:import [io.temporal.opentracing OpenTracingClientInterceptor OpenTracingWorkerInterceptor]))

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
  (c/create-workflow (get-client) workflow {:task-queue task-queue}))

(defn invoke [workflow]
  (let [i (create-workflow workflow)]
    (c/start i {})
    @(c/get-result i)))

;;-----------------------------------------------------------------------------
;; Workflows
;;-----------------------------------------------------------------------------

(defworkflow traced-workflow
  [ctx {:keys [args]}]
  (log/info "traced-workflow:" args)
  :ok)

;;-----------------------------------------------------------------------------
;; Fixtures
;;-----------------------------------------------------------------------------
(defn create-service []
  (let [env    (e/create {:workflow-client-options {:interceptors [(OpenTracingClientInterceptor.)]}
                          :worker-factory-options {:worker-interceptors [(OpenTracingWorkerInterceptor.)]}})
        client (e/get-client env)]
    (e/start env {:task-queue task-queue})
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
  (testing "Verifies that we can invoke our traced workflow" ;; todo: verify that tracing is working
    (is (-> (invoke traced-workflow) (= :ok)))))
