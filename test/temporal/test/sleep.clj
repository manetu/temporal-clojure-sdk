;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.test.sleep
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.test.utils :as t])
  (:import [java.time Duration]))

(use-fixtures :once t/wrap-service)

(defworkflow sleep-workflow
  [ctx {:keys [signals] :as args}]
  (log/info "sleep-workflow:" args)
  (w/sleep (Duration/ofSeconds 1))
  :ok)

(defn create []
  (let [wf (c/create-workflow (t/get-client) sleep-workflow {:task-queue t/task-queue})]
    (c/start wf nil)
    wf))

(deftest the-test
  (testing "Verifies that signals may timeout properly"
    (let [wf (create)]
      (is (= @(c/get-result wf) :ok)))))
