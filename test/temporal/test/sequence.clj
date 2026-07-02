;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.sequence
  (:require [clojure.test :refer :all]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]
            [temporal.promise :as pt]
            [temporal.exceptions :as e]
            [slingshot.slingshot :refer [throw+]]))

(use-fixtures :once t/wrap-service)

(defactivity sequence-activity
  [_ctx args]
  (log/info "greet-activity:" args)
  (str "Hi, " args))

(defworkflow sequence-workflow
  [_]
  @(-> (pt/resolved true)
       (p/then (fn [_]
                 (a/invoke sequence-activity "Bob")))
       (p/then (fn [_]
                 (a/invoke sequence-activity "Charlie")))
       (p/then (constantly :ok))))

(deftest then-test
  (testing "Verifies that we can process a promise-chain in sequence"
    (let [workflow (t/create-workflow sequence-workflow)]
      (c/start workflow nil)
      (is (= @(c/get-result workflow) :ok)))))

(defactivity error-throwing-activity
  [_ctx _args] (throw+ {:type ::error ::e/non-retriable? true}))

(defworkflow sequence-workflow-handle
  [{:keys [throw?]}]
  @(-> (pt/resolved true)
       (p/then (fn [_]
                 (if throw?
                   (a/invoke error-throwing-activity {})
                   (a/invoke sequence-activity "Alice"))))
       (p/handle (fn [_ err] (if err :threw :ok)))))

(deftest handle-test
  (testing "Verifies that we can process a promise-chain in sequence ending with handle"
    (let [workflow (t/create-workflow sequence-workflow-handle)]
      (c/start workflow {:throw? false})
      (is (= @(c/get-result workflow) :ok)))
    (let [workflow (t/create-workflow sequence-workflow-handle)]
      (c/start workflow {:throw? true})
      (is (= @(c/get-result workflow) :threw)))))
