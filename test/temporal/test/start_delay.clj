(ns temporal.test.start-delay
  (:require
   [clojure.test :refer :all]
   [temporal.client.core :as c]
   [temporal.test.utils :as t]
   [temporal.workflow :refer [defworkflow]])
  (:import
   [io.temporal.client WorkflowStub]
   [java.time Duration Instant]
   [java.time.temporal ChronoUnit TemporalAmount]))

(set! *warn-on-reflection* true)

(use-fixtures :once t/wrap-service)

(defworkflow delay-workflow
  [_args]
  :ok)

(defn create [options]
  (let [options (assoc options :task-queue t/task-queue)
        wf (c/create-workflow (t/get-client) delay-workflow options)]
    (c/start wf nil)
    wf))

(defn get-execution-time ^Instant [workflow]
  (.. ^WorkflowStub (:stub workflow)
      describe
      getExecutionTime))

(defn from-now ^Instant [^TemporalAmount delay]
  (.. (Instant/now)
      (plus delay)
      (truncatedTo ChronoUnit/SECONDS)))

(deftest the-test
  (testing "Verifies that the workflow got delayed"
    (let [start-delay (Duration/ofHours 2)
          expected (from-now start-delay)
          wf (create {:workflow-id "delayed-workflow"
                      :start-delay start-delay})
          execution-time (get-execution-time wf)]
      (is (.isBefore expected execution-time)))))

(deftest no-delay
  (testing "Verifies that the workflow is not delayed per default"
    (let [wf (create {:workflow-id "non-delayed-workflow"})
          execution-time (get-execution-time wf)
          now (from-now (Duration/ofSeconds 1))]
      (is (.isAfter now execution-time)))))
