;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.test.simple
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as w]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as test-utils]))

(use-fixtures :once test-utils/wrap-service)

(defactivity greet-activity
  [ctx {:keys [name] :as args}]
  (log/info "greet-activity:" args)
  (str "Hi, " name))

(defworkflow greeter-workflow
  [ctx {:keys [args]}]
  (log/info "greeter-workflow:" args)
  @(a/invoke greet-activity args))

(deftest basic-test
  (testing "Verifies that we can round-trip through start"
    (let [workflow (w/create-workflow (test-utils/get-client) greeter-workflow {:task-queue test-utils/task-queue})]
      (w/start workflow {:name "Bob"})
      (is (= @(w/get-result workflow) "Hi, Bob")))))
