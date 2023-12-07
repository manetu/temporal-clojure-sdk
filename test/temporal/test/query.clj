;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.query
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :refer [>!] :as c]
            [temporal.signals :refer [<!]]
            [temporal.workflow :as w]
            [temporal.workflow :refer [defworkflow]]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(def signal-name ::signal)
(def query-name ::query)

(defworkflow state-query-workflow
  [ctx {:keys [signals] {:keys [] :as args} :args}]
  (let [state (atom 0)]
    (w/register-query-handler! (fn [query-type args]
                                 @state))
    (dotimes [n 3]
      (<! signals signal-name)
      (swap! state inc))
    @state))

(deftest the-test
  (testing "Verifies that we can query a workflow's state"
    (let [workflow (t/create-workflow state-query-workflow)]
      (c/start workflow {})

      (>! workflow signal-name {})
      (>! workflow signal-name {})
      (is (= 2 (c/query workflow query-name {})))

      (>! workflow signal-name {})
      (is (= 3 (c/query workflow query-name {})))

      (is (= 3 (-> workflow c/get-result deref)))
      (is (= 3 (c/query workflow query-name {}))))))

