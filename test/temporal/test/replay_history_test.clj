;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.replay-history-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.datafy :as d]
            [clojure.java.io :as io]
            [temporal.testing.history :as history])
  (:import [io.temporal.common WorkflowExecutionHistory]
           [java.io File]))

(def sample-json
  "{\"events\":[{\"eventId\":\"1\",\"eventType\":\"EVENT_TYPE_WORKFLOW_EXECUTION_STARTED\",\"workflowExecutionStartedEventAttributes\":{}}]}")

(deftest from-json-test
  (testing "from-json parses a JSON string"
    (let [h (history/from-json sample-json)]
      (is (instance? WorkflowExecutionHistory h))
      (is (seq (.getEvents h)))))

  (testing "from-json 2-arity overrides workflow-id"
    (let [h (history/from-json sample-json "my-custom-id")]
      (is (= "my-custom-id" (.. h getWorkflowExecution getWorkflowId)))))

  (testing "from-json throws on malformed JSON"
    (is (thrown? Exception (history/from-json "{not valid json")))))

(deftest datafiable-test
  (testing "datafy returns a map with expected keys"
    (let [h (history/from-json sample-json)
          m (d/datafy h)]
      (is (map? m))
      (is (contains? m :workflow-id))
      (is (contains? m :run-id))
      (is (contains? m :events)))))

(deftest from-file-test
  (let [tmp (File/createTempFile "history-test" ".json")]
    (.deleteOnExit tmp)
    (spit tmp sample-json)

    (testing "from-file accepts a string path"
      (let [h (history/from-file (.getAbsolutePath tmp))]
        (is (instance? WorkflowExecutionHistory h))))

    (testing "from-file accepts a java.io.File"
      (let [h (history/from-file tmp)]
        (is (instance? WorkflowExecutionHistory h))))

    (testing "from-file accepts a java.nio.file.Path"
      (let [h (history/from-file (.toPath tmp))]
        (is (instance? WorkflowExecutionHistory h))))))

(deftest from-resource-test
  (testing "from-resource loads a classpath resource"
    (let [h (history/from-resource "histories/sample.json")]
      (is (instance? WorkflowExecutionHistory h))))

  (testing "from-resource throws ex-info when resource not found"
    (let [err (try (history/from-resource "nonexistent-history.json")
                   (catch clojure.lang.ExceptionInfo e e))]
      (is (re-find #"Classpath resource not found" (ex-message err)))
      (is (= "nonexistent-history.json" (:resource (ex-data err)))))))

(deftest ->json-test
  (testing "->json serializes to pretty-printed JSON by default"
    (let [h (history/from-json sample-json)
          j (history/->json h)]
      (is (string? j))
      (is (.contains j "EVENT_TYPE_WORKFLOW_EXECUTION_STARTED"))
      (is (.contains j "\n"))))

  (testing "->json accepts pretty-print? = false"
    (let [h (history/from-json sample-json)
          j (history/->json h false)]
      (is (string? j))))

  (testing "->json round-trip produces equivalent history"
    (let [h (history/from-json sample-json)
          j (history/->json h)
          h2 (history/from-json j)]
      (is (= (count (.getEvents h)) (count (.getEvents h2)))))))
