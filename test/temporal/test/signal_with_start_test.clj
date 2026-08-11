;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.signal-with-start-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.signals :refer [<!] :as s]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t])
  (:import [io.temporal.workflow Workflow]))

(use-fixtures :once t/wrap-service)

(def signal-name ::signal)

(defactivity signal-greet-activity
  [ctx {:keys [greeting name] :as args}]
  (log/info "greet-activity:" args)
  (str greeting ", " name))

(defworkflow signal-greeter-workflow
  [args]
  (log/info "greeter-workflow:" args)
  (let [signals (s/create-signal-chan)
        m (<! signals signal-name)]
    @(a/invoke signal-greet-activity (merge args m))))

(deftest the-test
  (testing "Verifies that we can round-trip through signal-with-start"
    (let [workflow (t/create-workflow signal-greeter-workflow)]
      (c/signal-with-start workflow signal-name {:name "Bob"} {:greeting "Hi"})
      (is (= @(c/get-result workflow) "Hi, Bob")))))

;;-----------------------------------------------------------------------------
;; Priority propagation test (Temporal Java SDK 1.38+)
;;
;; WorkflowOptions.priority was previously dropped by signalWithStart; it now
;; propagates to the started run, matching plain `start`.
;;-----------------------------------------------------------------------------

(defworkflow signal-priority-workflow
  [_args]
  (let [signals (s/create-signal-chan)]
    (<! signals signal-name)
    (-> (Workflow/getInfo) .getPriority .getPriorityKey)))

(deftest priority-propagation-test
  (testing "Verifies that :priority set at create-workflow propagates through signal-with-start (Temporal Java SDK 1.38+)"
    (let [workflow (c/create-workflow (t/get-client) signal-priority-workflow
                                      {:task-queue t/task-queue
                                       :priority {:priority-key 7}})]
      (c/signal-with-start workflow signal-name {} {})
      (is (= @(c/get-result workflow) 7)))))
