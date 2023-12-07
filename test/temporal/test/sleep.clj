;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.sleep
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.test.utils :as t]
            [promesa.core :as p])
  (:import [java.time Duration]))

(use-fixtures :once t/wrap-service)

(defworkflow sleep-workflow
  [ctx {:keys [signals] :as args}]
  (log/info "sleep-workflow:" args)
  (w/sleep (Duration/ofSeconds 1))
  :ok)

(def workflow-id "sleep-workflow")
(defn create []
  (let [wf (c/create-workflow (t/get-client) sleep-workflow {:task-queue t/task-queue
                                                             :workflow-id workflow-id})]
    (c/start wf nil)
    wf))

(deftest the-test
  (testing "Verifies that signals may timeout properly"
    (let [wf (create)]
      (is (= @(c/get-result wf) :ok)))))

(deftest terminate-sleep
  (testing "Verifies that sleeping workflows can be terminated"
    (let [wf (create)]
      (c/terminate (c/create-workflow (t/get-client) workflow-id) "reason" {})
      (is (= :error
             @(-> (c/get-result wf)
                  (p/catch io.temporal.client.WorkflowFailedException
                           (fn [_] :error))))))))
