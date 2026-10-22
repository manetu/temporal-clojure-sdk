;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.shutdown-options-test
  "Tests for worker shutdown/heartbeat options added in Temporal Java SDK 1.36"
  (:require [clojure.test :refer [deftest testing is]]
            [promesa.core :as p]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.client.core :as c]
            [temporal.testing.env :as e]
            [temporal.workflow :refer [defworkflow]])
  (:import [java.time Duration]))

(def task-queue ::shutdown-opts)

(defactivity noop-activity [_ _] :ok)

(defworkflow noop-workflow [_] @(a/invoke noop-activity nil))

(defn run-with-opts [worker-opts factory-opts]
  (let [env      (e/create factory-opts)
        client   (e/get-client env)
        _        (e/start env (merge {:task-queue task-queue} worker-opts))
        workflow (c/create-workflow client noop-workflow
                                    {:task-queue                task-queue
                                     :workflow-execution-timeout (Duration/ofSeconds 5)
                                     :retry-options             {:maximum-attempts 1}})]
    (c/start workflow {})
    @(-> (c/get-result workflow)
         (p/finally (fn [_ _] (e/synchronized-stop env))))))

(deftest allow-activity-heartbeat-during-shutdown-test
  (testing "worker accepts :allow-activity-heartbeat-during-shutdown true"
    (is (= :ok (run-with-opts {:allow-activity-heartbeat-during-shutdown true} {}))))
  (testing "worker accepts :allow-activity-heartbeat-during-shutdown false"
    (is (= :ok (run-with-opts {:allow-activity-heartbeat-during-shutdown false} {})))))

(deftest shutdown-check-interval-test
  (testing "worker-factory accepts :shutdown-check-interval"
    (is (= :ok (run-with-opts {}
                              {:worker-factory-options
                               {:shutdown-check-interval (Duration/ofMillis 10)}})))))
