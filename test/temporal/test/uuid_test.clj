;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.test.uuid-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.test.utils :as t]
            [temporal.side-effect :refer [gen-uuid]]))

(use-fixtures :once t/wrap-service)

(defworkflow uuid-workflow
  [ctx args]
  (log/info "workflow:" args)
  (gen-uuid))

(deftest the-test
  (testing "Verifies that we can use the side-effect safe gen-uuid"
    (let [workflow (t/create-workflow uuid-workflow)]
      (c/start workflow {})
      (let [r  @(c/get-result workflow)]
        (log/debug "r:" r)
        (is (some? r))))))
