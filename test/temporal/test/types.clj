;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.test.types
  (:require [clojure.test :refer :all]
            [temporal.internal.workflow :as workflow])
  (:import [java.time Duration]))

(deftest workflow-options
  (testing "Verify that our workflow options work"
    (let [x (workflow/wf-options-> {:workflow-id "foo"
                                    :task-queue "bar"
                                    :workflow-execution-timeout (Duration/ofSeconds 1)
                                    :workflow-run-timeout (Duration/ofSeconds 1)
                                    :workflow-task-timeout (Duration/ofSeconds 1)
                                    :retry-options {:maximum-attempts 1}
                                    :cron-schedule "* * * * *"
                                    :memo {"foo" "bar"}
                                    :search-attributes {"foo" "bar"}})]
      (is (-> x (.getWorkflowId) (= "foo")))
      (is (-> x (.getTaskQueue) (= "bar"))))))
