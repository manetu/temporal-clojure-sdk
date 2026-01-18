;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.continue-as-new
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defworkflow continue-as-new-workflow
  [{:keys [iteration max-iterations]}]
  (log/info "continue-as-new-workflow: iteration" iteration "of" max-iterations)
  (if (< iteration max-iterations)
    (w/continue-as-new {:iteration (inc iteration) :max-iterations max-iterations})
    {:completed true :final-iteration iteration}))

(deftest the-test
  (testing "Verifies that continue-as-new restarts workflow with new arguments"
    (let [workflow (t/create-workflow continue-as-new-workflow)]
      (c/start workflow {:iteration 1 :max-iterations 3})
      (is (= @(c/get-result workflow) {:completed true :final-iteration 3})))))
