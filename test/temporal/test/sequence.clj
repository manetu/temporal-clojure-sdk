;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns temporal.test.sequence
  (:require [clojure.test :refer :all]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]
            [temporal.promise :as pt]))

(use-fixtures :once t/wrap-service)

(defactivity greet-activity
  [ctx args]
  (log/info "greet-activity:" args)
  (str "Hi, " args))

(defworkflow sequence-workflow
  [ctx _]
  @(-> (pt/resolved true)
       (p/then (fn [_]
                 (a/invoke greet-activity "Bob")))
       (p/then (fn [_]
                 (a/invoke greet-activity "Charlie")))
       (p/then (constantly :ok))))

(deftest the-test
  (testing "Verifies that we can process a promise-chain in sequence"
    (let [workflow (t/create-workflow sequence-workflow)]
      (c/start workflow nil)
      (is (= @(c/get-result workflow) :ok)))))
