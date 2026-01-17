;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.upsert-search-attributes
  (:require [clojure.test :refer :all]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.testing.env :as e]
            [temporal.workflow :refer [defworkflow] :as w])
  (:import [java.time Duration OffsetDateTime]))

;; do not use the shared fixture, since we want to control the env creation with custom search attributes

(def task-queue ::default)

(defworkflow upsert-workflow
  [{:keys [initial-status final-status]}]
  (log/info "upsert-workflow: setting initial status" initial-status)
  (w/upsert-search-attributes {"CustomStatus" {:type :keyword :value initial-status}})
  ;; Simulate some work
  (w/sleep (Duration/ofMillis 10))
  (log/info "upsert-workflow: setting final status" final-status)
  (w/upsert-search-attributes {"CustomStatus" {:type :keyword :value final-status}})
  {:initial initial-status :final final-status})

(defworkflow multi-type-workflow
  [{:keys [text-val int-val double-val bool-val]}]
  (log/info "multi-type-workflow: setting various attribute types")
  (w/upsert-search-attributes {"TextAttr"   {:type :text :value text-val}
                               "IntAttr"    {:type :int :value int-val}
                               "DoubleAttr" {:type :double :value double-val}
                               "BoolAttr"   {:type :bool :value bool-val}})
  {:text text-val :int int-val :double double-val :bool bool-val})

(defn execute [initial-status final-status]
  (let [env      (e/create {:search-attributes {"CustomStatus" :keyword}})
        client   (e/get-client env)
        _        (e/start env {:task-queue task-queue})
        workflow (c/create-workflow client upsert-workflow {:task-queue task-queue
                                                            :workflow-execution-timeout (Duration/ofSeconds 10)
                                                            :retry-options {:maximum-attempts 1}})]
    (c/start workflow {:initial-status initial-status :final-status final-status})
    @(-> (c/get-result workflow)
         (p/finally (fn [_ _] (e/synchronized-stop env))))))

(defn execute-multi-type []
  (let [env      (e/create {:search-attributes {"TextAttr"   :text
                                                "IntAttr"    :int
                                                "DoubleAttr" :double
                                                "BoolAttr"   :bool}})
        client   (e/get-client env)
        _        (e/start env {:task-queue task-queue})
        workflow (c/create-workflow client multi-type-workflow {:task-queue task-queue
                                                                :workflow-execution-timeout (Duration/ofSeconds 10)
                                                                :retry-options {:maximum-attempts 1}})]
    (c/start workflow {:text-val "hello" :int-val 42 :double-val 3.14 :bool-val true})
    @(-> (c/get-result workflow)
         (p/finally (fn [_ _] (e/synchronized-stop env))))))

(deftest the-test
  (testing "Verifies that we can upsert search attributes during workflow execution"
    (is (= (execute "pending" "completed")
           {:initial "pending" :final "completed"}))))

(deftest multi-type-test
  (testing "Verifies that we can upsert various search attribute types"
    (is (= (execute-multi-type)
           {:text "hello" :int 42 :double 3.14 :bool true}))))
