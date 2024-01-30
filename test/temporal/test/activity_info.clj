;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.activity-info
  (:require [clojure.test :refer :all]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity getinfo-activity
  [ctx args]
  (a/get-info))

(defworkflow getinfo-workflow
  [args]
  @(a/invoke getinfo-activity args))

(deftest the-test
  (testing "Verifies that we can retrieve our activity-id"
    (let [workflow (t/create-workflow getinfo-workflow)]
      (c/start workflow {})
      (let [{:keys [activity-id]} @(c/get-result workflow)]
        (is (some? activity-id))))))

