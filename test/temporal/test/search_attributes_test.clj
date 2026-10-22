;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.search-attributes-test
  (:require [clojure.test :refer [deftest testing is]]
            [promesa.core :as p]
            [temporal.client.core :as c]
            [temporal.testing.env :as e]
            [temporal.workflow :refer [defworkflow]]
            [temporal.internal.search-attributes :as sa])
  (:import [java.time Duration OffsetDateTime]))

;; do not use the shared fixture, since we want to control the env creation

(def task-queue ::default)

(defworkflow searchable-workflow
  [args]
  :ok)

(defn execute [search-attributes]
  (let [env      (e/create {:search-attributes {"foo" :keyword}})
        client   (e/get-client env)
        _        (e/start env {:task-queue task-queue})
        workflow (c/create-workflow client searchable-workflow {:task-queue task-queue
                                                                :search-attributes search-attributes
                                                                :workflow-execution-timeout (Duration/ofSeconds 1)
                                                                :retry-options {:maximum-attempts 1}})]
    (c/start workflow {})
    [workflow
     @(-> (c/get-result workflow)
          (p/finally (fn [_ _] (e/synchronized-stop env))))]))

(defn get-search-attribute-value
  [workflow attr-name]
  (-> workflow
      :stub
      .getOptions
      .get
      .getTypedSearchAttributes
      sa/search-attributes->map
      (get attr-name)
      :value))

(deftest typed-search-attributes-test
  (testing "Verifies that we can utilize custom search attributes"
    (let [[workflow result] (execute {"foo" {:type :keyword :value "typed-attr"}})]
      (is (= result :ok))
      (is (= "typed-attr" (get-search-attribute-value workflow "foo"))))))

(deftest legacy-search-attributes-test
  (testing "Workflow creation accepts legacy search-attributes"
    (let [[workflow result] (execute {"foo" "legacy-attr"})]
      (is (= result :ok))
      (is (= "legacy-attr" (get-search-attribute-value workflow "foo"))))))

(deftest normalize-search-attributes-test
  (testing "normalize-search-attributes infers types for simple values and preserves typed maps"
    (let [expires-at (OffsetDateTime/now)
          input {"customer-name" "Alice Smith"
                 "customer-tier" :gold
                 "attempt" 3
                 "score" 98.6
                 "vip" true
                 "tags" ["a" :b 'c]
                 "expires-at" expires-at
                 "already-typed" {:type :keyword :value "x"}
                 "typed-unset" {:type :text :value nil}
                 "unset" nil}]
      (is (= {"customer-name" {:type :text :value "Alice Smith"}
              "customer-tier" {:type :keyword :value "gold"}
              "attempt" {:type :int :value 3}
              "score" {:type :double :value 98.6}
              "vip" {:type :bool :value true}
              "tags" {:type :keyword-list :value ["a" "b" "c"]}
              "expires-at" {:type :datetime :value expires-at}
              "already-typed" {:type :keyword :value "x"}
              "typed-unset" {:type :text :value nil}
              "unset" {:type :keyword :value nil}}
             (sa/normalize-search-attributes input)))
      (is (= {:type :keyword :value nil}
             (get (sa/normalize-search-attributes input) "unset"))))))
