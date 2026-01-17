;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.search-attributes
  (:require [clojure.test :refer :all]
            [promesa.core :as p]
            [temporal.client.core :as c]
            [temporal.testing.env :as e]
            [temporal.workflow :refer [defworkflow]])
  (:import [java.time Duration]))

;; do not use the shared fixture, since we want to control the env creation

(def task-queue ::default)

(defworkflow searchable-workflow
  [args]
  :ok)

(defn execute []
  (let [env      (e/create {:search-attributes {"foo" :keyword}})
        client   (e/get-client env)
        _        (e/start env {:task-queue task-queue})
        workflow (c/create-workflow client searchable-workflow {:task-queue task-queue
                                                                :search-attributes {"foo" "bar"}
                                                                :workflow-execution-timeout (Duration/ofSeconds 1)
                                                                :retry-options {:maximum-attempts 1}})]
    (c/start workflow {})
    @(-> (c/get-result workflow)
         (p/finally (fn [_ _] (e/synchronized-stop env))))))

(deftest the-test
  (testing "Verifies that we can utilize custom search attributes"
    (is (= (execute) :ok))))