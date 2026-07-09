;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.promesa-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [promesa.core :as p]
            [slingshot.slingshot :refer [throw+]]
            [taoensso.timbre :as log]
            [temporal.client.core :as c]
            [temporal.workflow :refer [defworkflow]]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.exceptions :as e]
            [temporal.test.utils :as t]))

(use-fixtures :once t/wrap-service)

(defactivity promesa-activity
  [ctx {:keys [name] :as args}]
  (log/info "activity:" args)
  (str "Hi, " name))

(defactivity promesa-error-activity
  [_ctx args]
  (log/info "error-activity:" args)
  (throw+ {:type ::error ::e/non-retriable? true}))

(defn- invoke
  "Invokes the error activity when :throw? is set, otherwise the happy-path activity."
  [{:keys [throw?] :as args}]
  (if throw?
    (a/invoke promesa-error-activity args)
    (a/invoke promesa-activity args)))

(defworkflow promesa-handle-workflow
  [args]
  (log/info "workflow:" args)
  @(-> (invoke args)
       (p/handle (fn [v e] (if e :threw v)))))

(defworkflow promesa-hmap-workflow
  [args]
  (log/info "workflow:" args)
  @(->> (invoke args)
        (p/hmap (fn [v e] (if e :threw v)))))

(defworkflow promesa-hcat-workflow
  [args]
  (log/info "workflow:" args)
  @(->> (invoke args)
        (p/hcat (fn [v e] (p/resolved (if e :threw v))))))

(defworkflow promesa-introspect-workflow
  [args]
  (log/info "workflow:" args)
  (let [pr             (invoke args)
        pending-before (p/pending? pr)
        printable?     (try (some? (pr-str pr)) (catch Throwable _ false))
        value          (try (p/await! pr) (catch Throwable _ :threw))]
    {:pending-before pending-before
     :printable?     printable?
     :value          value
     :resolved?      (p/resolved? pr)
     :rejected?      (p/rejected? pr)
     :pending-after  (p/pending? pr)}))

(defn- run
  [workflow args]
  (let [wf (t/create-workflow workflow)]
    (c/start wf args)
    @(c/get-result wf)))

(defn- verify-combinator
  "Runs both the success and error paths through a combinator workflow."
  [workflow]
  (is (= "Hi, Bob" (run workflow {:name "Bob"})))
  (is (= :threw    (run workflow {:name "Bob" :throw? true}))))

(deftest handle-test
  (testing "Verifies that p/handle propagates success values and routes errors"
    (verify-combinator promesa-handle-workflow)))

(deftest hmap-test
  (testing "Verifies that p/hmap propagates success values and routes errors"
    (verify-combinator promesa-hmap-workflow)))

(deftest hcat-test
  (testing "Verifies that p/hcat propagates success values and routes errors"
    (verify-combinator promesa-hcat-workflow)))

(defn- verify-introspect
  [args {:keys [value resolved? rejected?]}]
  (let [r (run promesa-introspect-workflow args)]
    (is (true? (:pending-before r)))
    (is (true? (:printable? r)))
    (is (= value     (:value r)))
    (is (= resolved? (:resolved? r)))
    (is (= rejected? (:rejected? r)))
    (is (false? (:pending-after r)))))

(deftest introspect-test
  (testing "Verifies that p/pending?, p/resolved?, p/rejected?, p/await!, and printing work on resolved and rejected promises"
    (verify-introspect {:name "Bob"}
                       {:value "Hi, Bob" :resolved? true :rejected? false})
    (verify-introspect {:name "Bob" :throw? true}
                       {:value :threw :resolved? false :rejected? true})))
