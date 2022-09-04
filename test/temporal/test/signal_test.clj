;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.test.signal-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :refer [>!] :as w]
            [temporal.signals :refer [<!]]
            [temporal.workflow :refer [defworkflow]]
            [temporal.test.utils :as test-utils]))

(use-fixtures :once test-utils/wrap-service)

(def signal-name ::signal)

(defn lazy-signals [signals]
  (lazy-seq (when-let [m (<! signals signal-name)]
              (cons m (lazy-signals signals)))))

(defworkflow test-workflow
  [ctx {:keys [signals] {:keys [expected] :as args} :args}]
  (log/info "test-workflow:" args)
  (doall (take expected (lazy-signals signals))))

(deftest basic-test
  (testing "Verifies that we can send signals directly"
    (let [expected 3
          workflow (w/create-workflow (test-utils/get-client) test-workflow {:task-queue test-utils/task-queue})]
      (w/start workflow {:expected expected})
      (dotimes [n expected]
        (>! workflow signal-name n))
      (is (= @(w/get-result workflow) '(0 1 2))))))
