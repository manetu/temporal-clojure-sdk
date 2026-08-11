;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.new-timer-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.test.utils :as t])
  (:import [io.temporal.workflow TimerOptions]
           [java.time Duration]))

(use-fixtures :once t/wrap-service)

(deftest timer-options-test
  (testing ":summary is set on TimerOptions"
    (let [^TimerOptions opts (w/timer-options-> {:summary "cooldown"})]
      (is (= "cooldown" (.getSummary opts))))))

(defworkflow new-timer-workflow
  [args]
  (log/info "new-timer-workflow:" args)
  @(w/new-timer (Duration/ofSeconds 1))
  :ok)

(defworkflow new-timer-with-options-workflow
  [args]
  (log/info "new-timer-with-options-workflow:" args)
  @(w/new-timer (Duration/ofSeconds 1) {:summary "cooldown"})
  :ok)

(deftest new-timer-fires-test
  (testing "new-timer returns a promise that resolves once the timer fires"
    (let [wf (c/create-workflow (t/get-client) new-timer-workflow {:task-queue t/task-queue})]
      (c/start wf nil)
      (is (= @(c/get-result wf) :ok)))))

(deftest new-timer-with-options-fires-test
  (testing "new-timer accepts a :summary option and still resolves"
    (let [wf (c/create-workflow (t/get-client) new-timer-with-options-workflow {:task-queue t/task-queue})]
      (c/start wf nil)
      (is (= @(c/get-result wf) :ok)))))
