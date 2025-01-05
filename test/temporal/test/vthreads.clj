;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.vthreads
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [promesa.core :as p]
            [promesa.exec :refer [vthreads-supported?]]
            [temporal.client.core :as c]
            [temporal.testing.env :as e]
            [temporal.workflow :refer [defworkflow]])
  (:import [java.time Duration]))

(def task-queue ::default)

(defworkflow vthread-workflow
  [args]
  (-> (Thread/currentThread)
      (.isVirtual)))

(defn execute [opts]
  (let [env      (e/create opts)
        client   (e/get-client env)
        _        (e/start env {:task-queue task-queue})
        workflow (c/create-workflow client vthread-workflow {:task-queue task-queue :workflow-execution-timeout (Duration/ofSeconds 1) :retry-options {:maximum-attempts 1}})]
    (c/start workflow {})
    @(-> (c/get-result workflow)
         (p/finally (fn [_ _]
                      (e/stop env))))))

(deftest the-test
  (testing "Verifies that we do not use vthreads by default"
    (is (false? (execute {}))))
  (testing "Verifies that we do not use vthreads if we specifically disable them"
    (is (false? (execute {:worker-factory-options {:using-virtual-workflow-threads false}}))))
  (testing "Verifies that we can enable vthread support"
    (if vthreads-supported?
      (is (true? (execute {:worker-factory-options {:using-virtual-workflow-threads true}})))
      (log/info "vthreads require JDK >= 21, skipping test"))))
