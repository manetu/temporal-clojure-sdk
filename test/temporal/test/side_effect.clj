;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.side-effect
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.side-effect :as side-effect]
            [temporal.test.utils :as t])
  (:import [java.time Instant]))

(use-fixtures :once t/wrap-service)

(defworkflow side-effect-workflow
  [args]
  (log/info "workflow:" args)
  (side-effect/now))

(deftest the-test
  (testing "Verifies that we can round-trip through start"
    (let [workflow (t/create-workflow side-effect-workflow)]
      (c/start workflow {})
      (is (instance? Instant @(c/get-result workflow))))))