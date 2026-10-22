;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.upsert-search-attributes-test
  (:require [clojure.test :refer [deftest testing is]]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.testing.env :as e]
            [temporal.workflow :refer [defworkflow] :as w])
  (:import [java.time Duration]))

;; do not use the shared fixture, since we want to control the env creation with custom search attributes

(def task-queue ::default)

(defworkflow upsert-workflow
  [{:keys [initial-status final-status]}]
  (log/info "upsert-workflow: setting initial status" initial-status)
  (w/upsert-search-attributes {"CustomStatus" initial-status})
  ;; Simulate some work
  (w/sleep (Duration/ofMillis 10))
  (log/info "upsert-workflow: setting final status" final-status)
  (w/upsert-search-attributes {"CustomStatus" final-status})
  {:initial initial-status :final final-status})

(defworkflow upsert-workflow-typed
  [{:keys [initial-status final-status]}]
  (log/info "upsert-workflow-typed: setting initial status" initial-status)
  (w/upsert-search-attributes {"CustomStatus" {:type :keyword :value initial-status}})
  ;; Simulate some work
  (w/sleep (Duration/ofMillis 10))
  (log/info "upsert-workflow-typed: setting final status" final-status)
  (w/upsert-search-attributes {"CustomStatus" {:type :keyword :value final-status}})
  {:initial initial-status :final final-status})

(defworkflow multi-type-workflow
  [{:keys [text-val int-val double-val bool-val]}]
  (w/upsert-search-attributes {"TextAttr" text-val
                               "IntAttr" int-val
                               "DoubleAttr" double-val
                               "BoolAttr" bool-val})
  {:text text-val :int int-val :double double-val :bool bool-val})

(defworkflow unset-with-nil-workflow
  [_]
  (w/upsert-search-attributes {"TextAttr" "hello"})
  (w/upsert-search-attributes {"TextAttr" nil})
  :ok)

(defn execute
  [workflow-fn initial-status final-status]
  (let [env      (e/create {:search-attributes {"CustomStatus" :keyword}})
        client   (e/get-client env)
        _        (e/start env {:task-queue task-queue})
        workflow (c/create-workflow client workflow-fn {:task-queue task-queue
                                                        :workflow-execution-timeout (Duration/ofSeconds 10)
                                                        :retry-options {:maximum-attempts 1}})]
    (c/start workflow {:initial-status initial-status :final-status final-status})
    @(-> (c/get-result workflow)
         (p/finally (fn [_ _] (e/synchronized-stop env))))))

(defn execute-multi-type []
  (let [env      (e/create {:search-attributes {"TextAttr" :text
                                                "IntAttr" :int
                                                "DoubleAttr" :double
                                                "BoolAttr" :bool}})
        client   (e/get-client env)
        _        (e/start env {:task-queue task-queue})
        workflow (c/create-workflow client multi-type-workflow {:task-queue task-queue
                                                                :workflow-execution-timeout (Duration/ofSeconds 10)
                                                                :retry-options {:maximum-attempts 1}})]
    (c/start workflow {:text-val "hello" :int-val 42 :double-val 3.14 :bool-val true})
    @(-> (c/get-result workflow)
         (p/finally (fn [_ _] (e/synchronized-stop env))))))

(defn execute-unset-with-nil []
  (let [env      (e/create {:search-attributes {"TextAttr" :text}})
        client   (e/get-client env)
        _        (e/start env {:task-queue task-queue})
        workflow (c/create-workflow client unset-with-nil-workflow {:task-queue task-queue
                                                                    :workflow-execution-timeout (Duration/ofSeconds 10)
                                                                    :retry-options {:maximum-attempts 1}})]
    (c/start workflow {})
    @(-> (c/get-result workflow)
         (p/finally (fn [_ _] (e/synchronized-stop env))))))

(deftest simple-upsert-test
  (testing "Verifies that we can upsert search attributes during workflow execution using simple values"
    (is (= (execute upsert-workflow "pending" "completed")
           {:initial "pending" :final "completed"}))))

(deftest typed-upsert-test
  (testing "Verifies that we can upsert search attributes during workflow execution using typed values"
    (is (= (execute upsert-workflow-typed "pending" "completed")
           {:initial "pending" :final "completed"}))))

(deftest multi-type-upsert-test
  (testing "Verifies that we can upsert multiple inferred search attribute types"
    (is (= {:text "hello" :int 42 :double 3.14 :bool true}
           (execute-multi-type)))))

(deftest unset-with-simple-nil-test
  (testing "Simple nil search-attribute value unsets successfully"
    (is (= :ok (execute-unset-with-nil)))))
