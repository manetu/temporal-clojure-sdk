;; Copyright © Manetu, Inc.  All rights reserved

(ns temporal.test.types
  (:require [clojure.test :refer :all]
            [temporal.client.core :as client]
            [temporal.client.worker :as worker])
  (:import [java.time Duration]
           [io.grpc Grpc InsecureChannelCredentials Metadata]
           [io.grpc.netty.shaded.io.grpc.netty GrpcSslContexts]))

(deftest workflow-options
  (testing "Verify that our workflow options work"
    (let [x (client/wf-options-> {:workflow-id "foo"
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
    (let [x (client/stub-options-> {:channel                    (-> (Grpc/newChannelBuilder "foo:1234" (InsecureChannelCredentials/create))
                                                                    (.build))
                                    :ssl-context              (-> (GrpcSslContexts/forClient)
                                                                  (.build))
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
    (let [x (client/client-options-> {:identity "test"
                                      :namespace "test"})]
      (is (-> x (.getIdentity) (= "test")))
      (is (-> x (.getNamespace) (= "test")))))
  (testing "Verify that mixed client/stub options work"
    (let [options {:target "foo:1234" :namespace "default"}]
      (is (some? (client/stub-options-> options)))
      (is (some? (client/client-options-> options))))))

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
