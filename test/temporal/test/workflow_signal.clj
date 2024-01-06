;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.workflow-signal
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.signals :refer [<! >!] :as s]
            [temporal.workflow :refer [defworkflow]]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(def signal-name ::signal)

(defworkflow wfsignal-primary-workflow
  [args]
  (log/info "primary-workflow:" args)
  (let [signals (s/create-signal-chan)]
    (<! signals signal-name)))

(defworkflow wfsignal-secondary-workflow
  [{:keys [workflow-id msg]}]
  (log/info "secondary-workflow:" msg)
  (>! workflow-id signal-name msg))

(deftest the-test
  (testing "Verifies that we can send signals from a workflow"
    (let [p-wf (c/create-workflow (t/get-client) wfsignal-primary-workflow {:task-queue t/task-queue :workflow-id "primary"})
          s-wf (t/create-workflow wfsignal-secondary-workflow)]
      (c/start p-wf nil)
      (c/start s-wf {:workflow-id "primary" :msg "Hi, Bob"})
      (is (= @(c/get-result p-wf) "Hi, Bob")))))
