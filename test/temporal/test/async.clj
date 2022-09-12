;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.test.async
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [go]]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity async-greet-activity
  [ctx {:keys [name] :as args}]
  (go
    (log/info "greet-activity:" args)
    (if (= name "Charlie")
      (ex-info "permission-denied" {})                      ;; we don't like Charlie
      (str "Hi, " name))))

(defworkflow async-greeter-workflow
  [ctx {:keys [args]}]
  (log/info "greeter-workflow:" args)
  @(a/invoke async-greet-activity args (assoc a/default-invoke-options :retry-options {:maximum-attempts 1})))

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
