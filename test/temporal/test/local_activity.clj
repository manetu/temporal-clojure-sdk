;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.local-activity
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity local-greet-activity
  [ctx {:keys [name] :as args}]
  (log/info "greet-activity:" args)
  (str "Hi, " name))

(defworkflow local-greeter-workflow
  [ctx {:keys [args]}]
  (log/info "greeter-workflow:" args)
  @(a/local-invoke local-greet-activity args {:do-not-include-args true}))

(deftest the-test
  (testing "Verifies that we can round-trip through start"
    (let [workflow (t/create-workflow local-greeter-workflow)]
      (c/start workflow {:name "Bob"})
      (is (= @(c/get-result workflow) "Hi, Bob")))))
