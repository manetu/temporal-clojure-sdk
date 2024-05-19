;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.concurrency
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.client.core :as c]
            [temporal.promise :as pt]
            [temporal.test.utils :as t]
            [temporal.workflow :refer [defworkflow] :as w]))

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

(deftest concurrency-with-all-test
  (testing "Verifies that we can launch activities in parallel"
    (let [workflow (t/create-workflow concurrency-workflow)]
      (c/start workflow {})
      (is (-> workflow c/get-result deref count (= 10))))))

(defactivity all-settled-activity
  [ctx args]
  (log/tracef "calling all-settled-activity %d" args)
  args)

(defn invoke-settled [x]
  (a/invoke all-settled-activity x))

(defworkflow all-settled-workflow
  [args]
  (log/trace "calling all-settled-workflow")
  @(-> (pt/all-settled (map invoke-settled (range 10)))
       (p/then (fn [r] r))
       (p/catch (fn [e] (:args (ex-data e))))))

(defactivity error-prone-activity
  [ctx args]
  (log/tracef "calling error-prone-activity %d" args)
  (when (= args 5)
    (throw (ex-info "error on 5" {:args args})))
  args)

(defn invoke-error [x]
  (a/invoke error-prone-activity x))

(defworkflow error-prone-workflow
  [args]
  (log/trace "calling error-prone-workflow")
  @(-> (pt/all-settled (map invoke-error (range 10)))
       (p/then (fn [r] r))
       (p/catch (fn [e] (:args (ex-data e))))))

(deftest concurrency-with-all-settled-test
  (testing "Testing that all-settled waits for all the activities to complete
            just like `p/all` does in spite of errors"
    (let [workflow (t/create-workflow all-settled-workflow)]
      (c/start workflow {})
      (is (-> workflow c/get-result deref count (= 10)))))
  (testing "Testing that all-settled waits for all the activities to complete
                just like `p/all` and can still propogate errors"
    (let [workflow (t/create-workflow error-prone-workflow)]
      (c/start workflow {})
      (is (-> workflow c/get-result deref (= 5))))))

(defactivity doubling-activity
  [ctx args]
  (log/info "doubling-activity:" args)
  (* args 2))

(defn invoke-doubling-activity [x]
  (a/invoke doubling-activity x))

(defworkflow concurrent-child-workflow
  [args]
  (log/info "concurrent-child-workflow:" args)
  @(-> (pt/all [(invoke-doubling-activity args)])
       (p/then (fn [r] (log/info "r:" r) r))))

(defn invoke-child-workflow [x]
  (w/invoke concurrent-child-workflow x {:retry-options {:maximum-attempts 1} :task-queue t/task-queue}))

(defworkflow concurrent-parent-workflow
  [args]
  (log/info "concurrent-parent-workflow:" args)
  @(-> (pt/all (map invoke-child-workflow (range 10)))
       (p/then (fn [r] (log/info "r:" r) r))))

(deftest child-workflow-concurrency-test
  (testing "Using a child workflow instead of an ctivity works with the promise api"
    (let [workflow (t/create-workflow concurrent-parent-workflow)]
      (c/start workflow {})
      (is (-> workflow c/get-result deref count (= 10)))
      (is (-> workflow c/get-result) (mapv #(* 2 %) (range 10))))))
