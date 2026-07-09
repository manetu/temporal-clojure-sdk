;; Copyright © 2024 Manetu, Inc.  All rights reserved
;;
(ns temporal.test.cancellation-token-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity cancellation-token-probe
  [_ _]
  (let [token (a/get-cancellation-token)]
    {:cancelled?        (a/cancellation-requested? token)
     :future-present?   (some? (a/cancellation-future token))}))

(defworkflow cancellation-token-workflow
  [_]
  @(a/invoke cancellation-token-probe {}))

(deftest the-test
  (testing "Verifies that the cancellation token is accessible and initially not cancelled"
    (let [workflow (t/create-workflow cancellation-token-workflow)]
      (c/start workflow {})
      (let [result (-> workflow c/get-result deref)]
        (is (false? (:cancelled? result)))
        (is (true? (:future-present? result)))))))
