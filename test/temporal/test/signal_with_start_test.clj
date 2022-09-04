;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.test.signal-with-start-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as w]
            [temporal.signals :refer [<!]]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as test-utils]))

(use-fixtures :once test-utils/wrap-service)

(def signal-name ::signal)

(defactivity greet-activity
  [ctx {:keys [greeting name] :as args}]
  (log/info "greet-activity:" args)
  (str greeting ", " name))

(defworkflow greeter-workflow
  [ctx {:keys [args signals]}]
  (log/info "greeter-workflow:" args)
  (let [m (<! signals signal-name)]
    @(a/invoke greet-activity (merge args m))))

(deftest basic-test
  (testing "Verifies that we can round-trip through signal-with-start"
    (let [workflow (w/create-workflow (test-utils/get-client) greeter-workflow {:task-queue test-utils/task-queue})]
      (w/signal-with-start workflow signal-name {:name "Bob"} {:greeting "Hi"})
      (is (= @(w/get-result workflow) "Hi, Bob")))))
