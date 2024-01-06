;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.raw-signal
  (:require [clojure.test :refer :all]
            [temporal.client.core :refer [>!] :as c]
            [temporal.signals :refer [<!] :as s]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(def signal-name ::signal)

(defworkflow raw-signal-workflow
  [args]
  (let [state (atom 0)]
    (s/register-signal-handler! (fn [signal-name args]
                                  (swap! state inc)))
    (w/await (fn [] (> @state 1)))
    @state))

(deftest the-test
  (testing "Verifies that we can handle raw signals"
    (let [workflow (t/create-workflow raw-signal-workflow)]
      (c/start workflow {})

      (>! workflow signal-name {})
      (>! workflow signal-name {})
      (is (= 2 @(c/get-result workflow))))))
