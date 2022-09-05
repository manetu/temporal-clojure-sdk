;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.test.simple
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity greet-activity
  [ctx {:keys [name] :as args}]
  (log/info "greet-activity:" args)
  (str "Hi, " name))

(defworkflow greeter-workflow
  [ctx {:keys [args]}]
  (log/info "greeter-workflow:" args)
  @(a/invoke greet-activity args))

(deftest the-test
  (testing "Verifies that we can round-trip through start"
    (let [workflow (t/create-workflow greeter-workflow)]
      (c/start workflow {:name "Bob"})
      (is (= @(c/get-result workflow) "Hi, Bob")))))
