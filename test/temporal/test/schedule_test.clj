(ns temporal.test.schedule-test
  (:require [clojure.test :refer [deftest testing is]]
            [temporal.client.schedule :as schedule]
            [temporal.internal.schedule :as s]
            [temporal.internal.search-attributes :as sa]
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
    (pause [_ note]
      (swap! state update :pause assoc :note note))
    (unpause [_]
      (swap! state update :unpause (fnil inc 0)))
    (unpause [_ note]
      (swap! state update :unpause assoc :note note))
    (backfill [_ backfills]
      (swap! state update :backfill assoc :backfills backfills))
    (trigger [_]
      (swap! state update :trigger (fnil inc 0)))
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
                             :task-queue t/task-queue
                             :search-attributes {"foo" {:type :keyword :value "workflow"}}}
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
                     :backfills [{:start-at (Instant/parse "2024-01-01T00:00:00Z")
                                  :end-at (Instant/parse "2024-01-01T01:00:00Z")
                                  :overlap :buffer-one}]
                     :memo {"note" "memo"}
                     :search-attributes {"foo" {:type :keyword :value "schedule"}}}
                    schedule)})

;; NOTE: Temporal Schedules are not supported in the Temporal test environment
;; hence the mocked ScheduleClient stubs for now

(deftest schedule-workflow-test
  (testing "scheduling a workflow is successful"
    (let [state (atom {})
          client (create-mocked-schedule-client state)]
      (is (some? (schedule/schedule client schedule-id (stub-schedule-options))))
      (let [schedule (get-in @state [:create :schedule])
            schedule-options (get-in @state [:create :schedule-options])
            ^io.temporal.client.schedules.ScheduleBackfill backfill (first (.getBackfills schedule-options))]
        (is (= (get-in @state [:create :schedule-id]) schedule-id))
        (is (= (-> schedule .getAction .getWorkflowType) (w/get-annotated-name simple-workflow)))
        (is (= (-> schedule .getAction .getWorkflowType) "simple-workflow"))
        (is (= (-> schedule .getAction .getOptions .getWorkflowId) workflow-id))
        (is (= (-> schedule
                   .getAction
                   .getOptions
                   .getTypedSearchAttributes
                   sa/search-attributes->map
                   (get "foo")
                   :value)
               "workflow"))
        (is (= (-> schedule .getSpec .getCronExpressions) ["0 * * * * "]))
        (is (-> schedule .getPolicy .isPauseOnFailure))
        (is (= (-> schedule .getState .getNote) "note"))
        (is (-> schedule .getState .isPaused))
        (is (= (count (.getBackfills schedule-options)) 1))
        (is (= (.getStartAt backfill) (Instant/parse "2024-01-01T00:00:00Z")))
        (is (= (.getEndAt backfill) (Instant/parse "2024-01-01T01:00:00Z")))
        (is (= (.getOverlapPolicy backfill) (s/overlap-policy-> :buffer-one)))
        (is (= (-> schedule-options
                   .getTypedSearchAttributes
                   sa/search-attributes->map
                   (get "foo")
                   :value)
               "schedule"))))))

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

(deftest pause-scheduled-workflow-with-note-test
  (testing "pauses a scheduled workflow with a note"
    (let [state (atom {})
          client (create-mocked-schedule-client state)]
      (schedule/pause client schedule-id "paused for maintenance")
      (is (= (get-in @state [:pause :note]) "paused for maintenance")))))

(deftest unpause-scheduled-workflow-with-note-test
  (testing "unpauses a scheduled workflow with a note"
    (let [state (atom {})
          client (create-mocked-schedule-client state)]
      (schedule/unpause client schedule-id "maintenance complete")
      (is (= (get-in @state [:unpause :note]) "maintenance complete")))))

(deftest backfill-scheduled-workflow-test
  (testing "backfills a scheduled workflow successfully"
    (let [state (atom {})
          client (create-mocked-schedule-client state)
          backfills [{:start-at (Instant/parse "2024-01-03T00:00:00Z")
                      :end-at (Instant/parse "2024-01-04T00:00:00Z")
                      :overlap :buffer}]]
      (schedule/backfill client schedule-id backfills)
      (let [^io.temporal.client.schedules.ScheduleBackfill backfill (first (get-in @state [:backfill :backfills]))]
        (is (= 1 (count (get-in @state [:backfill :backfills]))))
        (is (= (.getStartAt backfill) (Instant/parse "2024-01-03T00:00:00Z")))
        (is (= (.getEndAt backfill) (Instant/parse "2024-01-04T00:00:00Z")))
        (is (= (.getOverlapPolicy backfill) (s/overlap-policy-> :buffer)))))))

(deftest execute-scheduled-workflow-test
  (testing "executes a scheduled workflow is successful"
    (let [state (atom {})
          client (create-mocked-schedule-client state)]
      (schedule/execute client schedule-id :skip)
      (is (= (get-in @state [:trigger :overlap-policy])
             (s/overlap-policy-> :skip))))))

(deftest execute-scheduled-workflow-with-default-overlap-test
  (testing "executes a scheduled workflow with the Java SDK default overlap behavior"
    (let [state (atom {})
          client (create-mocked-schedule-client state)]
      (schedule/execute client schedule-id)
      (is (= (:trigger @state) 1)))))

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

(deftest reschedule-updates-search-attributes-test
  (testing "reschedule updates schedule-level search attributes"
    (let [state (atom {})
          client (create-mocked-schedule-client state)
          update-options (stub-schedule-options
                          :schedule {:search-attributes {"foo" {:type :keyword :value "updated"}}})
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
      (is (= (-> (get-in @state [:update :update-fn])
                 (.apply schedule-update-input)
                 (.getTypedSearchAttributes)
                 sa/search-attributes->map
                 (get "foo")
                 :value)
             "updated")))))

(deftest reschedule-preserves-search-attributes-test
  (testing "reschedule without :search-attributes leaves typed search attributes unset"
    (let [state (atom {})
          client (create-mocked-schedule-client state)
          update-options (-> (stub-schedule-options :spec {:cron-expressions ["1 * * * *"]})
                             (update :schedule dissoc :search-attributes))
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
      (is (nil? (-> (get-in @state [:update :update-fn])
                    (.apply schedule-update-input)
                    (.getTypedSearchAttributes)))))))
