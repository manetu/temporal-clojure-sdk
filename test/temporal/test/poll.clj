;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.poll
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.signals :as s]
            [temporal.workflow :refer [defworkflow]]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(def signal-name ::signal)

(defn lazy-signals [signals]
  (lazy-seq (when-let [m (s/poll signals signal-name)]
              (cons m (lazy-signals signals)))))

(defworkflow poll-workflow
  [ctx {:keys [signals]}]
  (log/info "test-workflow:")
  (doall (lazy-signals signals)))

(deftest the-test
  (testing "Verifies that poll exits with nil when there are no signals"
    (let [workflow (t/create-workflow poll-workflow)]
      (c/start workflow {})
      (is (-> workflow c/get-result deref count (= 0)))))
  (testing "Verifies that we receive signals with poll correctly"
    (let [workflow (t/create-workflow poll-workflow)]
      (c/signal-with-start workflow signal-name {:foo "bar"} {})
      (is (-> workflow c/get-result deref first (get :foo) (= "bar"))))))
