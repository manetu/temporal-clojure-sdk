;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.workflow-update
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.signals :as s]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defworkflow update-workflow
  [args]
  (let [state (atom {:counter 0})
        signals (s/create-signal-chan)]
    (w/register-update-handler!
     (fn [update-type args]
       (log/info "update-handler: type=" update-type "args=" args)
       (case update-type
         :increment (do
                      (swap! state update :counter + (:amount args))
                      @state)
         :get-state @state
         nil)))
    ;; Wait for done signal
    (s/<! signals ::done)
    @state))

(defworkflow validated-update-workflow
  [args]
  (let [state (atom {:counter 0})
        signals (s/create-signal-chan)]
    (w/register-update-handler!
     (fn [update-type args]
       (log/info "validated-update-handler: type=" update-type "args=" args)
       (case update-type
         :increment (do
                      (swap! state update :counter + (:amount args))
                      @state)
         nil))
     {:validator (fn [update-type args]
                   (log/info "validator: type=" update-type "args=" args)
                   (when (and (= update-type :increment)
                              (neg? (:amount args)))
                     (throw (IllegalArgumentException. "amount must be positive"))))})
    ;; Wait for done signal
    (s/<! signals ::done)
    @state))

(deftest update-test
  (testing "Verifies that updates can modify workflow state and return values"
    (let [workflow (t/create-workflow update-workflow)]
      (c/start workflow {})
      ;; Send an update immediately after start - no sleep needed
      (let [result (c/update workflow :increment {:amount 5})]
        (is (= {:counter 5} result)))
      ;; Send another update
      (let [result (c/update workflow :increment {:amount 3})]
        (is (= {:counter 8} result)))
      ;; Signal workflow to complete
      (c/>! workflow ::done {})
      ;; Verify final workflow result
      (is (= {:counter 8} @(c/get-result workflow))))))

(deftest validated-update-test
  (testing "Verifies that update validators work correctly"
    (let [workflow (t/create-workflow validated-update-workflow)]
      (c/start workflow {})
      ;; Valid update should succeed
      (let [result (c/update workflow :increment {:amount 5})]
        (is (= {:counter 5} result)))
      ;; Invalid update should fail validation
      (is (thrown? Exception (c/update workflow :increment {:amount -3})))
      ;; State should be unchanged after failed validation
      (let [result (c/update workflow :increment {:amount 2})]
        (is (= {:counter 7} result)))
      ;; Signal workflow to complete
      (c/>! workflow ::done {})
      ;; Verify final workflow result
      (is (= {:counter 7} @(c/get-result workflow))))))
