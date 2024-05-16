;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.concurrency
  (:require [clojure.test :refer :all]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.client.core :as c]
            [temporal.promise :as pt]
            [temporal.test.utils :as t]
            [temporal.workflow :refer [defworkflow]]
            [temporal.workflow :as w]))

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

(defactivity doubling-activity
  [ctx args]
  (* args 2))

(defworkflow concurrent-child-workflow
  [args]
  (log/info "concurrent-child-workflow:" args)
  @(a/invoke doubling-activity args))

(defn invoke-child-workflow [x]
  (w/invoke concurrent-child-workflow x))

(defworkflow concurrent-parent-workflow
  [args]
  (log/info "concurrent-parent-workflow:" args)
  @(-> (pt/all (map invoke-child-workflow (range 10)))
       (p/then (fn [r]
                 (log/info "r:" r)
                 r))))

(deftest child-workflow-concurrency
  (testing "Verifies that we can launch child workflows in parallel"
    (let [workflow (t/create-workflow concurrent-parent-workflow)]
      (c/start workflow {})
      (is (-> workflow c/get-result deref count (= 10)))
      (is (-> workflow c/get-result) (mapv #(* 2 %) (range 10))))))
