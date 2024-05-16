;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.async
  (:require [clojure.core.async :refer [go]]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [taoensso.timbre :as log]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.client.core :as c]
            [temporal.test.utils :as t]
            [temporal.workflow :refer [defworkflow] :as w]))

(use-fixtures :once t/wrap-service)

(defactivity async-greet-activity
  [ctx {:keys [name] :as args}]
  (go
    (log/info "greet-activity:" args)
    (if (= name "Charlie")
      (ex-info "permission-denied" {})
      (str "Hi, " name))))

(defworkflow async-greeter-workflow
  [args]
  (log/info "greeter-workflow:" args)
  @(a/invoke async-greet-activity args {:retry-options {:maximum-attempts 1}}))

(deftest the-test
  (testing "Verifies that we can round-trip with an async task"
    (let [workflow (t/create-workflow async-greeter-workflow)]
      (c/start workflow {:name "Bob"})
      (is (= @(c/get-result workflow) "Hi, Bob"))))
  (testing "Verifies that we can process errors in async mode"
    (let [workflow (t/create-workflow async-greeter-workflow)]
      (c/start workflow {:name "Charlie"})
      (is (thrown? java.util.concurrent.ExecutionException
                   @(c/get-result workflow))))))

(defactivity async-child-activity
  [ctx {:keys [name] :as args}]
  (go
    (log/info "async-child-activity:" args)
    (if (= name "Charlie")
      (ex-info "permission-denied" {})
      (str "Hi, " name))))

(defworkflow async-child-workflow
  [{:keys [name] :as args}]
  (log/info "async-child-workflow:" args)
  @(a/invoke async-child-activity args {:retry-options {:maximum-attempts 1}}))

(defworkflow async-parent-workflow
  [args]
  (log/info "async-parent-workflow:" args)
  @(w/invoke async-child-workflow args {:retry-options {:maximum-attempts 1} :task-queue t/task-queue}))

(deftest child-workflow-test
  (testing "Verifies that we can round-trip with an async task"
    (let [workflow (t/create-workflow async-parent-workflow)]
      (c/start workflow {:name "Bob"})
      (is (= @(c/get-result workflow) "Hi, Bob"))))
  (testing "Verifies that we can process errors in async mode"
    (let [workflow (t/create-workflow async-parent-workflow)]
      (c/start workflow {:name "Charlie"})
      (is (thrown? java.util.concurrent.ExecutionException
                   @(c/get-result workflow))))))
