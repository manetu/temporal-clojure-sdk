;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.resolved-promises
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.promise :as pt]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defworkflow all-workflow
  [args]
  (log/info "workflow:" args)
  @(pt/all [(pt/resolved true)])
  @(pt/race [(pt/resolved true)])
  :ok)

(deftest the-test
  (testing "Verifies that pt/resolved and pt/rejected are compatible with all/race"
    (let [workflow (t/create-workflow all-workflow)]
      (c/start workflow {})
      (is (-> workflow c/get-result deref (= :ok))))))
