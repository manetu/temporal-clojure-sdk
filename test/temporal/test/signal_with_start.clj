;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.signal-with-start
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.signals :refer [<!] :as s]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(def signal-name ::signal)

(defactivity signal-greet-activity
  [ctx {:keys [greeting name] :as args}]
  (log/info "greet-activity:" args)
  (str greeting ", " name))

(defworkflow signal-greeter-workflow
  [args]
  (log/info "greeter-workflow:" args)
  (let [signals (s/create-signal-chan)
        m (<! signals signal-name)]
    @(a/invoke signal-greet-activity (merge args m))))

(deftest the-test
  (testing "Verifies that we can round-trip through signal-with-start"
    (let [workflow (t/create-workflow signal-greeter-workflow)]
      (c/signal-with-start workflow signal-name {:name "Bob"} {:greeting "Hi"})
      (is (= @(c/get-result workflow) "Hi, Bob")))))
