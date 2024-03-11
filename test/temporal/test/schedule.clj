(ns temporal.test.schedule
  (:require [clojure.test :refer :all]
            [temporal.client.schedule :as schedule]
            [temporal.internal.schedule :as s]
            [temporal.internal.workflow :as w]
            [temporal.test.utils :as t]
            [temporal.workflow :refer [defworkflow]])
  (:import [io.temporal.client.schedules ScheduleClient ScheduleHandle ScheduleUpdateInput ScheduleDescription]
           [java.time Duration Instant]))

(def workflow-id "simple-workflow")
(def schedule-id "simple-workflow-schedule")
(defworkflow simple-workflow [ctx args] args)

(defn create-mocked-schedule-handle
  [state]
  (reify ScheduleHandle
    (update [_ update-fn]
      (swap! state update :update assoc :update-fn update-fn))
    (delete [_]
      (swap! state update :delete (fnil inc 0)))
    (describe [_]
      (swap! state update :describe (fnil inc 0))
      nil)
    (pause [_]
      (swap! state update :pause (fnil inc 0)))
    (unpause [_]
      (swap! state update :unpause (fnil inc 0)))
    (trigger [_ overlap-policy]
      (swap! state update :trigger assoc :overlap-policy overlap-policy))))

(defn create-mocked-schedule-client
  [state]
  (reify ScheduleClient
    (createSchedule [_ schedule-id schedule schedule-options]
      (swap! state update :create assoc
             :schedule-id schedule-id
             :schedule schedule
             :schedule-options schedule-options)
      (create-mocked-schedule-handle state))
    (getHandle [_ schedule-id]
      (swap! state update :handle assoc :schedule-id schedule-id)
      (create-mocked-schedule-handle state))))

(defn- stub-schedule-options
  [& {:keys [action spec policy state schedule]}]
  {:action (merge {:arguments {:name "John Doe" :age 32}
                   :options {:workflow-id workflow-id
                             :task-queue t/task-queue}
                   :workflow-type simple-workflow}
                  action)
   :spec (merge {:cron-expressions ["0 * * * * "]
                 :end-at (Instant/now)
                 :jitter (Duration/ofSeconds 1)
                 :start-at (Instant/now)
                 :timezone "US/Central"}
                spec)
   :policy (merge {:pause-on-failure? true
                   :catchup-window (Duration/ofSeconds 1)
                   :overlap :skip}
                  policy)
   :state (merge {:paused? true
                  :note "note"
                  :limited-action? false}
                 state)
   :schedule (merge {:trigger-immediately? true
                     :memo "memo"}
                    schedule)})

;; NOTE: Temporal Schedules are not supported in the Temporal test environment
;; hence the mocked ScheduleClient stubs for now

(deftest schedule-workflow-test
  (testing "scheduling a workflow is successful"
    (let [state (atom {})
          client (create-mocked-schedule-client state)]
      (is (some? (schedule/schedule client schedule-id (stub-schedule-options))))
      (is (= (get-in @state [:create :schedule-id]) schedule-id))
      (is (= (-> (get-in @state [:create :schedule]) .getAction .getWorkflowType) (w/get-annotated-name simple-workflow)))
      (is (= (-> (get-in @state [:create :schedule]) .getAction .getWorkflowType) "simple-workflow"))
      (is (= (-> (get-in @state [:create :schedule]) .getAction .getOptions .getWorkflowId) workflow-id))
      (is (= (-> (get-in @state [:create :schedule]) .getSpec .getCronExpressions) ["0 * * * * "]))
      (is (-> (get-in @state [:create :schedule]) .getPolicy .isPauseOnFailure))
      (is (= (-> (get-in @state [:create :schedule]) .getState .getNote) "note"))
      (is (-> (get-in @state [:create :schedule]) .getState .isPaused)))))

(deftest unschedule-scheduled-workflow-test
  (testing "unscheduling a scheduled workflow is successful"
    (let [state (atom {})
          client (create-mocked-schedule-client state)]
      (schedule/unschedule client schedule-id)
      (is (= (:delete @state) 1)))))

(deftest describe-scheduled-workflow-test
  (testing "describing a scheduled workflow is successful"
    (let [state (atom {})
          client (create-mocked-schedule-client state)]
      (schedule/describe client schedule-id)
      (is (= (:describe @state) 1)))))

(deftest pause-scheduled-workflow-test
  (testing "pauses a scheduled workflow is successful"
    (let [state (atom {})
          client (create-mocked-schedule-client state)]
      (schedule/pause client schedule-id)
      (is (= (:pause @state) 1)))))

(deftest unpause-scheduled-workflow-test
  (testing "unpauses a scheduled workflow is successful"
    (let [state (atom {})
          client (create-mocked-schedule-client state)]
      (schedule/unpause client schedule-id)
      (is (= (:unpause @state) 1)))))

(deftest execute-scheduled-workflow-test
  (testing "executes a scheduled workflow is successful"
    (let [state (atom {})
          client (create-mocked-schedule-client state)]
      (schedule/execute client schedule-id :skip)
      (is (= (get-in @state [:trigger :overlap-policy])
             (s/overlap-policy-> :skip))))))

(deftest reschedule-scheduled-workflow-test
  (testing "reschedules/updates a scheduled workflow is successful"
    (let [state (atom {})
          client (create-mocked-schedule-client state)
          update-options (stub-schedule-options :spec {:cron-expressions ["1 * * * *"]})
          schedule-update-input (ScheduleUpdateInput.
                                 (ScheduleDescription.
                                  schedule-id
                                  nil
                                  (s/schedule-> (stub-schedule-options))
                                  nil
                                  nil
                                  nil
                                  nil))]
      (schedule/reschedule client schedule-id update-options)
      ;; validate the actual update function works
      (is (= (-> (get-in @state [:update :update-fn])
                 (.apply schedule-update-input)
                 (.getSchedule)
                 (.getSpec)
                 (.getCronExpressions))
             (-> (s/schedule-> update-options)
                 (.getSpec)
                 (.getCronExpressions)))))))
