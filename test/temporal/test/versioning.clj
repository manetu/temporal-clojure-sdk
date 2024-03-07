;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.versioning
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

;; Only serves to generate events in our history
(defactivity versioned-activity
  [ctx args]
  :ok)

(defworkflow versioned-workflow
  [args]
  (log/info "versioned-workflow:" args)
  @(a/invoke versioned-activity args)

  (case (w/get-version ::test w/default-version 1)
    w/default-version @(a/invoke versioned-activity args)
    1 @(a/local-invoke versioned-activity args)))

(deftest the-test
  (testing "Verifies that we can version a workflow"
    (let [client (t/get-client)
          wf (c/create-workflow client versioned-workflow {:task-queue t/task-queue :workflow-id "test"})]
      (c/start wf {})
      (is (= @(c/get-result wf) :ok)))))
