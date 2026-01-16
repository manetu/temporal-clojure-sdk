;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.vthreads
  (:require [clojure.string :as string]
            [clojure.test :refer :all]
            [promesa.core :as p]
            [taoensso.timbre :as log]
            [temporal.activity :refer [defactivity] :as a]
            [temporal.client.core :as c]
            [temporal.testing.env :as e]
            [temporal.workflow :refer [defworkflow]])
  (:import [java.time Duration]))

;; Check for virtual thread support (Java 21+)
(def vthreads-supported?
  (try
    (Class/forName "java.lang.VirtualThread")
    true
    (catch ClassNotFoundException _ false)))

(def task-queue ::default)

(defactivity collect-platform-threads [_ _]
  (->> (Thread/getAllStackTraces)
       (keys)
       (map Thread/.getName)
       (into #{})))

(defworkflow vthread-workflow
  [_args]
  {:virtual-worker-thread? (.isVirtual (Thread/currentThread))
   :platform-threads @(a/invoke collect-platform-threads nil)})

(defn execute [backend-opts worker-opts]
  (let [env      (e/create backend-opts)
        client   (e/get-client env)
        _        (e/start env (merge {:task-queue task-queue} worker-opts))
        workflow (c/create-workflow client vthread-workflow {:task-queue task-queue :workflow-execution-timeout (Duration/ofSeconds 1) :retry-options {:maximum-attempts 1}})
        _ (c/start workflow {})]
    @(-> (c/get-result workflow)
         (p/finally (fn [_ _] (e/synchronized-stop env))))))

(defn- substring-in-coll? [substr coll]
  (boolean (some (fn [s] (string/includes? s substr)) coll)))

(deftest the-test
  (testing "Verifies that we do not use vthreads by default"
    (is (false? (:virtual-worker-thread? (execute {} {})))))
  (testing "Verifies that we do not use vthreads if we specifically disable them"
    (is (false? (:virtual-worker-thread? (execute {:worker-factory-options {:using-virtual-workflow-threads false}}
                                                  {})))))
  (if-not vthreads-supported?
    (log/info "vthreads require JDK >= 21, skipping tests")
    (testing "Verifies that we can enable vthread support"
      (is (true? (:virtual-worker-thread?
                  (execute {:worker-factory-options {:using-virtual-workflow-threads true}}
                           {}))))

      (testing "Verifies that Poller and Executor threads can be turned into vthreads using worker-options"
        (let [pthreads (:platform-threads (execute {} {:using-virtual-threads true}))]
          (is (not-any? #(substring-in-coll? % pthreads)
                        ["Workflow Executor" "Activity Executor"
                         "Workflow Poller"   "Activity Poller"])))

        (let [pthreads (:platform-threads (execute {} {:using-virtual-threads false}))]
          (is (every? #(substring-in-coll? % pthreads)
                      ["Workflow Executor" "Activity Executor"
                       "Workflow Poller"   "Activity Poller"])))))))
