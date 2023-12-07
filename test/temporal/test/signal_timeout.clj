;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.signal-timeout
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :refer [>!] :as c]
            [temporal.signals :refer [<!]]
            [temporal.workflow :refer [defworkflow]]
            [temporal.test.utils :as t])
  (:import [java.time Duration]))

(use-fixtures :once t/wrap-service)

(def signal-name ::signal)

(defworkflow timeout-workflow
  [ctx {:keys [signals] :as args}]
  (log/info "timeout-workflow:" args)
  (or (<! signals signal-name (Duration/ofSeconds 1))
      :timed-out))

(defn create []
  (let [wf (c/create-workflow (t/get-client) timeout-workflow {:task-queue t/task-queue})]
    (c/start wf nil)
    wf))

(deftest the-test
  (testing "Verifies that signals may timeout properly"
    (let [wf (create)]
      (is (= @(c/get-result wf) :timed-out))))
  (testing "Verifies that signals are received properly even when a timeout is requested"
    (let [wf (create)]
      (>! wf ::signal "Hi, Bob")
      (is (= @(c/get-result wf) "Hi, Bob")))))
