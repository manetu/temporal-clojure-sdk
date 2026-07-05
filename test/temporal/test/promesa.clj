;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.promesa
  (:require [clojure.test :refer :all]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity promesa-activity
  [ctx {:keys [name] :as args}]
  (log/info "activity:" args)
  (str "Hi, " name))

(defworkflow promesa-handle-workflow
  [args]
  (log/info "workflow:" args)
  @(-> (a/invoke promesa-activity args)
       (p/handle (fn [v _e] v))))

(defworkflow promesa-hmap-workflow
  [args]
  (log/info "workflow:" args)
  @(->> (a/invoke promesa-activity args)
        (p/hmap (fn [v _e] v))))

(defworkflow promesa-hcat-workflow
  [args]
  (log/info "workflow:" args)
  @(->> (a/invoke promesa-activity args)
        (p/hcat (fn [v _e] (p/resolved v)))))

(defworkflow promesa-introspect-workflow
  [args]
  (log/info "workflow:" args)
  (let [pr (a/invoke promesa-activity args)
        pending-before (p/pending? pr)
        printable?     (try (some? (pr-str pr)) (catch Throwable _ false))
        value          (p/await pr)]
    {:pending-before pending-before
     :printable?     printable?
     :value          value
     :resolved?      (p/resolved? pr)
     :rejected?      (p/rejected? pr)
     :pending-after  (p/pending? pr)}))

(deftest handle-test
  (testing "Verifies that we can round-trip through p/handle"
    (let [workflow (t/create-workflow promesa-handle-workflow)]
      (c/start workflow {:name "Bob"})
      (is (= @(c/get-result workflow) "Hi, Bob")))))

(deftest hmap-test
  (testing "Verifies that we can round-trip through p/hmap"
    (let [workflow (t/create-workflow promesa-hmap-workflow)]
      (c/start workflow {:name "Bob"})
      (is (= @(c/get-result workflow) "Hi, Bob")))))

(deftest hcat-test
  (testing "Verifies that we can round-trip through p/hcat"
    (let [workflow (t/create-workflow promesa-hcat-workflow)]
      (c/start workflow {:name "Bob"})
      (is (= @(c/get-result workflow) "Hi, Bob")))))

(deftest introspect-test
  (testing "Verifies that p/pending?, p/resolved?, p/rejected?, p/await, and printing work"
    (let [workflow (t/create-workflow promesa-introspect-workflow)]
      (c/start workflow {:name "Bob"})
      (let [{:keys [pending-before printable? value resolved? rejected? pending-after]}
            @(c/get-result workflow)]
        (is (true? pending-before))
        (is (true? printable?))
        (is (= "Hi, Bob" value))
        (is (true? resolved?))
        (is (false? rejected?))
        (is (false? pending-after))))))
