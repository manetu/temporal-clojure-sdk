;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.types
  (:require [clojure.test :refer :all]
            [temporal.client.worker :as worker]
            [temporal.client.options :as o]
            [temporal.internal.workflow :as w]
            [temporal.internal.schedule :as s]
            [temporal.internal.child-workflow :as cw])
  (:import [java.time Duration Instant]
           [io.grpc Grpc InsecureChannelCredentials Metadata]
           [io.grpc.netty.shaded.io.grpc.netty GrpcSslContexts]))

(deftest workflow-options
  (testing "Verify that our workflow options work"
    (let [x (w/wf-options-> {:workflow-id "foo"
                             :task-queue "bar"
                             :workflow-execution-timeout (Duration/ofSeconds 1)
                             :workflow-run-timeout (Duration/ofSeconds 1)
                             :workflow-task-timeout (Duration/ofSeconds 1)
                             :retry-options {:maximum-attempts 1}
                             :cron-schedule "* * * * *"
                             :memo {"foo" "bar"}
                             :search-attributes {"foo" "bar"}})]
      (is (-> x (.getWorkflowId) (= "foo")))
      (is (-> x (.getTaskQueue) (= "bar"))))))

(deftest client-options
  (testing "Verify that our stub options work"
    (let [x (o/stub-options-> {:channel                  (-> (Grpc/newChannelBuilder "foo:1234" (InsecureChannelCredentials/create)) (.build))
                               :ssl-context              (-> (GrpcSslContexts/forClient) (.build))
                               :target                   "foo:1234"
                               :enable-https             false
                               :rpc-timeout              (Duration/ofSeconds 1)
                               :rpc-long-poll-timeout    (Duration/ofSeconds 1)
                               :rpc-query-timeout        (Duration/ofSeconds 1)
                               :backoff-reset-freq       (Duration/ofSeconds 1)
                               :grpc-reconnect-freq      (Duration/ofSeconds 1)
                               :headers                  (Metadata.)
                               :enable-keepalive         true
                               :keepalive-time           (Duration/ofSeconds 1)
                               :keepalive-timeout        (Duration/ofSeconds 1)
                               :keepalive-without-stream true})]
      (is (-> x (.getTarget) (= "foo:1234")))))
  (testing "Verify that our client options work"
    (let [x (o/workflow-client-options-> {:identity "test"
                                          :namespace "test"})]
      (is (-> x (.getIdentity) (= "test")))
      (is (-> x (.getNamespace) (= "test")))))
  (testing "Verify that mixed client/stub options work"
    (let [options {:target "foo:1234" :namespace "default"}]
      (is (some? (o/stub-options-> options)))
      (is (some? (o/workflow-client-options-> options))))))

(deftest worker-options
  (testing "Verify that our worker-options work"
    (let [x (worker/worker-options-> {:max-concurrent-activity-task-pollers            2
                                      :max-concurrent-activity-execution-size          10
                                      :max-concurrent-local-activity-execution-size    2
                                      :max-concurrent-workflow-task-pollers            2
                                      :max-concurrent-workflow-task-execution-size     10
                                      :default-deadlock-detection-timeout              1000
                                      :default-heartbeat-throttle-interval             (Duration/ofSeconds 10)
                                      :max-heartbeat-throttle-interval                 (Duration/ofSeconds 10)
                                      :local-activity-worker-only                      false
                                      :max-taskqueue-activities-per-second             1.0
                                      :max-workers-activities-per-second               1.0})]
      (is (-> x (.isLocalActivityWorkerOnly) false?)))))

(deftest schedule-client-options
  (testing "Verify that our stub options work"
    (let [x (o/stub-options-> {:channel                  (-> (Grpc/newChannelBuilder "foo:1234" (InsecureChannelCredentials/create)) (.build))
                               :ssl-context              (-> (GrpcSslContexts/forClient) (.build))
                               :target                   "foo:1234"
                               :enable-https             false
                               :rpc-timeout              (Duration/ofSeconds 1)
                               :rpc-long-poll-timeout    (Duration/ofSeconds 1)
                               :rpc-query-timeout        (Duration/ofSeconds 1)
                               :backoff-reset-freq       (Duration/ofSeconds 1)
                               :grpc-reconnect-freq      (Duration/ofSeconds 1)
                               :headers                  (Metadata.)
                               :enable-keepalive         true
                               :keepalive-time           (Duration/ofSeconds 1)
                               :keepalive-timeout        (Duration/ofSeconds 1)
                               :keepalive-without-stream true})]
      (is (-> x (.getTarget) (= "foo:1234")))))
  (testing "Verify that our client options work"
    (let [x (o/schedule-client-options-> {:identity "test"
                                          :namespace "test"})]
      (is (-> x (.getIdentity) (= "test")))
      (is (-> x (.getNamespace) (= "test")))))
  (testing "Verify that mixed client/stub options work"
    (let [options {:target "foo:1234" :namespace "default"}]
      (is (some? (o/stub-options-> options)))
      (is (some? (o/schedule-client-options-> options))))))

(deftest schedule-options
  (testing "Verify that a schedule can be constructed with the action, spec, policy, and state"
    (let [action {:arguments {:value 1}
                  :options {:workflow-id "my-workflow-execution"
                            :task-queue "queue"}
                  :workflow-type "my-workflow"}
          spec {:cron-expressions ["0 * * * * "]
                :end-at (Instant/now)
                :jitter (Duration/ofSeconds 1)
                :start-at (Instant/now)
                :timezone "US/Central"}
          policy {:pause-on-failure? true
                  :catchup-window (Duration/ofSeconds 1)
                  :overlap :skip}
          state {:paused? true
                 :note "note"
                 :limited-action? false}
          schedule (s/schedule-> {:action action
                                  :policy policy
                                  :spec spec
                                  :state state})]
      (is (some? (s/schedule-action-start-workflow-> action)))
      (is (some? (s/schedule-spec-> spec)))
      (is (some? (s/schedule-policy-> policy)))
      (is (some? (s/schedule-state-> state)))
      (is (some? schedule))
      (is (= "my-workflow" (-> schedule .getAction .getWorkflowType)))
      (is (= "my-workflow-execution" (-> schedule .getAction .getOptions .getWorkflowId)))
      (is (= ["0 * * * * "] (-> schedule .getSpec .getCronExpressions)))
      (is (-> schedule .getPolicy .isPauseOnFailure))
      (is (= "note" (-> schedule .getState .getNote)))
      (is (-> schedule .getState .isPaused)))))

(deftest child-workflow-options
  (testing "Verify that a `ChildWorkflowOptions` instance can be built properly"
    (let [options {:workflow-id "foo"
                   :task-queue "bar"
                   :workflow-execution-timeout (Duration/ofSeconds 1)
                   :workflow-run-timeout (Duration/ofSeconds 2)
                   :workflow-task-timeout (Duration/ofSeconds 3)
                   :retry-options {:maximum-attempts 1}
                   :cron-schedule "* * * * *"
                   :memo {"foo" "bar"}
                   :workflow-id-reuse-policy :terminate-if-running
                   :parent-close-policy :terminate
                   :cancellation-type :abandon}
          child-workflow-options (cw/child-workflow-options-> options)]
      (is (some? child-workflow-options))
      (is (= "foo" (-> child-workflow-options .getWorkflowId )))
      (is (= "bar" (-> child-workflow-options .getTaskQueue)))
      (is (= (Duration/ofSeconds 1) (-> child-workflow-options .getWorkflowExecutionTimeout)))
      (is (= (Duration/ofSeconds 2) (-> child-workflow-options .getWorkflowRunTimeout)))
      (is (= (Duration/ofSeconds 3) (-> child-workflow-options .getWorkflowTaskTimeout)))
      (is (= 1 (-> child-workflow-options .getRetryOptions .getMaximumAttempts)))
      (is (= "* * * * *"  (-> child-workflow-options .getCronSchedule)))
      (is (= {"foo" "bar"}  (-> child-workflow-options .getMemo)))
      (is (= io.temporal.api.enums.v1.WorkflowIdReusePolicy/WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING
             (-> child-workflow-options .getWorkflowIdReusePolicy)))
      (is (= io.temporal.api.enums.v1.ParentClosePolicy/PARENT_CLOSE_POLICY_TERMINATE
             (-> child-workflow-options .getParentClosePolicy)))
      (is (= io.temporal.workflow.ChildWorkflowCancellationType/ABANDON
             (-> child-workflow-options .getCancellationType))))))
