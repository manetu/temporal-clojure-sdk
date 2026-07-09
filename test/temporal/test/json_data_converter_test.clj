;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.json-data-converter-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.client.core :as c]
            [temporal.test.utils :as t]
            [temporal.workflow :refer [defworkflow] :as w])
  (:import [java.time Duration]))

(defn wrap-json-service [test-fn]
  (t/create-service {:data-converter t/data-converter})
  (try
    (test-fn)
    (finally
      (t/destroy-service))))

(use-fixtures :once wrap-json-service)

(defactivity json-greet-activity
  [_ {:keys [name]}]
  {:message (str "Hi, " name)})

(defworkflow json-greeter-workflow
  [args]
  @(a/invoke json-greet-activity args))

(deftest json-data-converter-test
  (testing "round-trips workflow & activity for JSON converter"
    (let [workflow (t/create-workflow json-greeter-workflow)]
      (c/start workflow {:name "Bob"})
      (is (= {:message "Hi, Bob"}
             @(c/get-result workflow))))))

(defworkflow json-state-workflow
  [args]
  (let [state (atom {:name (:name args)
                     :count 0})]
    (w/register-query-handler!
     (fn [query-type query-args]
       (case query-type
         :state @state
         {:unknown-query query-type
          :args query-args})))
    (w/register-update-handler!
     (fn [update-type update-args]
       (case update-type
         :increment (do
                      (swap! state update :count + (:amount update-args))
                      @state)
         {:unknown-update update-type
          :args update-args})))
    (w/sleep (Duration/ofMillis 500))
    @state))

(deftest json-query-and-update-test
  (testing "round-trips query and update data through JSON converter"
    (let [workflow (t/create-workflow json-state-workflow)]
      (c/start workflow {:name "Bob"})
      (is (= {:name "Bob" :count 5}
             (c/update workflow :increment {:amount 5})))
      (is (= {:name "Bob" :count 5}
             (c/query workflow :state {})))
      (is (= {:name "Bob" :count 5}
             @(c/get-result workflow))))))
