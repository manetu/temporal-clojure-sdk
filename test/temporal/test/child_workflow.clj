(ns temporal.test.child-workflow
  (:require [clojure.test :refer :all]
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
  @(w/invoke child-workflow args))

(deftest basic-child-workflow-test
  (testing "Verifies that we call a child workflow in a parent workflow"
    (let [workflow (t/create-workflow parent-workflow)]
      (c/start workflow {:names ["Bob" "George" "Fred"]})
      (is (= (set @(c/get-result workflow)) #{"Hi, Bob" "Hi, George" "Hi, Fred"})))))
