;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.legacy-workflow
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :refer [>!] :as c]
            [temporal.signals :refer [<!] :as s]
            [temporal.workflow :refer [defworkflow]]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(def signal-name ::signal)

(defworkflow legacy-workflow
  [_ {:keys [signals args]}]
  (log/info "legacy-workflow:" args)
  (<! signals signal-name))

(deftest the-test
  (testing "Verifies that we support a legacy workflow with [ctx {:keys [signals]}"
    (let [workflow (t/create-workflow legacy-workflow)]
      (c/start workflow {})
      (>! workflow signal-name :foo)
      (is (-> workflow c/get-result deref (= :foo))))))
