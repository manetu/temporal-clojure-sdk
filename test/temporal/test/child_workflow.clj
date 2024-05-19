(ns temporal.test.child-workflow
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow] :as w]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity child-greeter-activity
  [ctx {:keys [name] :as args}]
  (log/info "greet-activity:" args)
  (str "Hi, " name))

(defworkflow child-workflow
  [{:keys [names] :as args}]
  (log/info "child-workflow:" args)
  (for [name names]
    @(a/invoke child-greeter-activity {:name name})))

(defworkflow parent-workflow
  [args]
  (log/info "parent-workflow:" args)
  @(w/invoke child-workflow args {:retry-options {:maximum-attempts 1} :task-queue t/task-queue}))

(deftest basic-child-workflow-test
  (testing "Using a child workflow (with multiple activities) in a parent workflow works as expected"
    (let [workflow (t/create-workflow parent-workflow)]
      (c/start workflow {:names ["Bob" "George" "Fred"]})
      (is (= (set @(c/get-result workflow)) #{"Hi, Bob" "Hi, George" "Hi, Fred"})))))

(defworkflow parent-workflow-with-activities
  [args]
  (log/info "parent-workflow:" args)
  (concat
   @(w/invoke child-workflow args)
   [@(a/invoke child-greeter-activity {:name "Xavier"})]))

(deftest parent-workflow-with-mulitple-test
  (testing "Using a child workflow (with multiple activities) in a parent workflow (with activities) works as expected"
    (let [workflow (t/create-workflow parent-workflow-with-activities)]
      (c/start workflow {:names ["Bob" "George" "Fred"]})
      (is (= (set @(c/get-result workflow)) #{"Hi, Bob" "Hi, George" "Hi, Fred" "Hi, Xavier"})))))
