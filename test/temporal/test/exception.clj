;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.exception
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity exception-activity
  [ctx args]
  (log/info "exception-activity:" args)
  (throw (ex-info "test 1" {})))

(defworkflow indirect-exception-workflow
  [args]
  (log/info "indirect-exception-workflow:" args)
  @(a/invoke exception-activity args {:retry-options {:maximum-attempts 1}}))

(defworkflow direct-exception-workflow
  [args]
  (log/info "direct-exception-workflow:" args)
  (throw (ex-info "test 2" {})))

(deftest the-test
  (testing "Verifies that we can throw exceptions indirectly from an activity"
    (let [workflow (t/create-workflow indirect-exception-workflow)]
      (c/start workflow {})
      (is (thrown? Exception @(c/get-result workflow)))))
  (testing "Verifies that we can throw exceptions directly from a workflow"
    (let [workflow (t/create-workflow direct-exception-workflow)]
      (c/start workflow {})
      (is (thrown? Exception @(c/get-result workflow))))))
