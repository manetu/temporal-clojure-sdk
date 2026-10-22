;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.current-details-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defworkflow current-details-workflow
  [_args]
  (let [before (w/get-current-details)]
    (w/set-current-details "in progress")
    {:before before :after (w/get-current-details)}))

(deftest current-details-test
  (testing "set-current-details/get-current-details round-trip (Temporal Java SDK 1.36+, stable in 1.38)"
    (let [wf (c/create-workflow (t/get-client) current-details-workflow {:task-queue t/task-queue})]
      (c/start wf nil)
      (is (= @(c/get-result wf) {:before nil :after "in progress"})))))
