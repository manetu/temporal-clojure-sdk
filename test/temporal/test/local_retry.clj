;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.local-retry
  (:require [clojure.test :refer :all]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t])
  (:import (io.temporal.client WorkflowFailedException)
           [io.temporal.failure TimeoutFailure ActivityFailure]
           [java.time Duration]))

(use-fixtures :once t/wrap-service)

(defactivity local-retry-activity
  [ctx args]
  (log/info "local-retry-activity")
  (Thread/sleep 100000000))

(defworkflow local-retry-workflow
  [args]
  (log/info "local-retry-workflow:" args)
  @(-> (a/local-invoke local-retry-activity {} (merge args {:do-not-include-args true
                                                            :start-to-close-timeout (Duration/ofMillis 500)}))
       (p/catch ActivityFailure
                :fail)))

(defn exec [args]
  (let [workflow (t/create-workflow local-retry-workflow)]
    (c/start workflow args)
    @(-> (c/get-result workflow)
         (p/then (constantly :fail))
         (p/catch WorkflowFailedException
                  (fn [ex]
                    (if (instance? TimeoutFailure (ex-cause ex))
                      :pass
                      :fail))))))

(deftest the-test
  (testing "RetryPolicy defaults"
    (is (= :pass (exec {}))))
  (testing "Explicit unlimited"
    (is (= :pass (exec {:retry-options {:maximum-attempts 0}}))))
  (testing "Verify that setting maximum-attempts to a finite value is respected"
    (is (= :fail (exec {:retry-options {:maximum-attempts 1}})))))
