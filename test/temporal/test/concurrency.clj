;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.concurrency
  (:require [clojure.test :refer :all]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.promise :as pt]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity concurrency-activity
  [ctx args]
  (log/info "activity:" args)
  args)

(defn invoke [x]
  (a/invoke concurrency-activity x))

(defworkflow concurrency-workflow
  [args]
  (log/info "workflow:" args)
  @(-> (pt/all (map invoke (range 10)))
       (p/then (fn [r]
                 (log/info "r:" r)
                 r))))

(deftest the-test
  (testing "Verifies that we can launch activities in parallel"
    (let [workflow (t/create-workflow concurrency-workflow)]
      (c/start workflow {})
      (is (-> workflow c/get-result deref count (= 10))))))

(defactivity all-settled-activity
  [ctx args] args)

(defworkflow all-settled-workflow
  [args]
  @(-> (pt/all (map #(a/invoke all-settled-activity %) (range 10)))
       (p/then (fn [r] r))
       (p/catch (fn [e] (:args (ex-data e))))))

(defactivity error-prone-activity
  [ctx args]
  (when (= args 5)
    (throw (ex-info "error on 5" {:args args})))
  args)

(defworkflow error-prone-workflow
  [args]
  @(-> (pt/all (map #(a/invoke error-prone-activity %) (range 10)))
       (p/then (fn [r] r))
       (p/catch (fn [e] (:args (ex-data e))))))

(deftest test-all-settled
  (testing "Testing that all-settled waits for all the activities to complete
            just like `p/all` does in spite of errors"
    (let [workflow (t/create-workflow all-settled-workflow)]
      (c/start workflow {})
      (is (-> workflow c/get-result deref count (= 10)))))
  (testing "Testing that all-settled waits for all the activities to complete
            despite error and can return the errors"
    (let [workflow (t/create-workflow error-prone-workflow)]
      (c/start workflow {})
      (is (-> workflow c/get-result deref (= 5))))))
