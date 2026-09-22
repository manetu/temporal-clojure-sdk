(ns ^:no-doc temporal.internal.schedule
  (:require [temporal.internal.search-attributes :as sa]
            [temporal.internal.utils :as u]
            [temporal.internal.workflow :as w])
  (:import [io.temporal.api.enums.v1 ScheduleOverlapPolicy]
           [io.temporal.client.schedules
            Schedule
            ScheduleUpdate
            Schedule$Builder
            ScheduleActionStartWorkflow
            ScheduleActionStartWorkflow$Builder
            ScheduleOptions
            ScheduleOptions$Builder
            SchedulePolicy
            SchedulePolicy$Builder
            ScheduleSpec
            ScheduleSpec$Builder
            ScheduleState
            ScheduleState$Builder]))

(defn overlap-policy->
  [overlap-policy]
  (case overlap-policy
    :allow ScheduleOverlapPolicy/SCHEDULE_OVERLAP_POLICY_ALLOW_ALL
    :buffer ScheduleOverlapPolicy/SCHEDULE_OVERLAP_POLICY_BUFFER_ALL
    :buffer-one ScheduleOverlapPolicy/SCHEDULE_OVERLAP_POLICY_BUFFER_ONE
    :cancel ScheduleOverlapPolicy/SCHEDULE_OVERLAP_POLICY_CANCEL_OTHER
    :skip ScheduleOverlapPolicy/SCHEDULE_OVERLAP_POLICY_SKIP
    :terminate ScheduleOverlapPolicy/SCHEDULE_OVERLAP_POLICY_TERMINATE_OTHER))

(def schedule-policy-spec
  {:catchup-window        #(.setCatchupWindow ^SchedulePolicy$Builder %1 %2)
   :overlap               (fn [^SchedulePolicy$Builder builder value]
                            (.setOverlap builder (overlap-policy-> value)))
   :pause-on-failure?     #(.setPauseOnFailure ^SchedulePolicy$Builder %1 %2)})

(defn schedule-policy->
  ^SchedulePolicy [params]
  (u/build (SchedulePolicy/newBuilder) schedule-policy-spec params))

(def schedule-spec-spec
  {:calendars             #(.setCalendars ^ScheduleSpec$Builder %1 %2)
   :cron-expressions      #(.setCronExpressions ^ScheduleSpec$Builder %1 %2)
   :intervals             #(.setIntervals ^ScheduleSpec$Builder %1 %2)
   :end-at                #(.setEndAt ^ScheduleSpec$Builder %1 %2)
   :jitter                #(.setJitter ^ScheduleSpec$Builder %1 %2)
   :skip-at               #(.setSkip ^ScheduleSpec$Builder %1 %2)
   :start-at              #(.setStartAt ^ScheduleSpec$Builder %1 %2)
   :timezone              #(.setTimeZoneName ^ScheduleSpec$Builder %1 %2)})

(defn schedule-spec->
  ^ScheduleSpec [params]
  (u/build (ScheduleSpec/newBuilder) schedule-spec-spec params))

(def schedule-action-start-workflow-spec->
  {:options               #(.setOptions ^ScheduleActionStartWorkflow$Builder %1 (w/wf-options-> %2))
   :arguments             (fn [^ScheduleActionStartWorkflow$Builder builder value]
                            (.setArguments builder (u/->objarray value)))
   :workflow-type         (fn [^ScheduleActionStartWorkflow$Builder builder value]
                            (if (string? value)
                              (.setWorkflowType builder ^String value)
                              (.setWorkflowType builder (w/get-annotated-name value))))})

(defn schedule-action-start-workflow->
  ^ScheduleActionStartWorkflow [params]
  (u/build
   (ScheduleActionStartWorkflow/newBuilder)
   schedule-action-start-workflow-spec-> params))

(def schedule-state-spec
  {:limited-action?       #(.setLimitedAction ^ScheduleState$Builder %1 %2)
   :note                  #(.setNote ^ScheduleState$Builder %1 %2)
   :paused?               #(.setPaused ^ScheduleState$Builder %1 %2)
   :remaining-actions     #(.setRemainingActions ^ScheduleState$Builder %1 %2)})

(defn schedule-state->
  ^ScheduleState [params]
  (u/build (ScheduleState/newBuilder) schedule-state-spec params))

(def schedule-options-spec
  {:memo                  #(.setMemo ^ScheduleOptions$Builder %1 %2)
   :search-attributes     #(.setTypedSearchAttributes ^ScheduleOptions$Builder %1 (sa/search-attributes-> %2))
   :trigger-immediately?  #(.setTriggerImmediately ^ScheduleOptions$Builder %1 %2)})

(defn schedule-options->
  ^ScheduleOptions [params]
  (u/build (ScheduleOptions/newBuilder) schedule-options-spec params))

(def schedule-spec
  {:action                #(.setAction ^Schedule$Builder %1 (schedule-action-start-workflow-> %2))
   :policy                #(.setPolicy ^Schedule$Builder %1 (schedule-policy-> %2))
   :spec                  #(.setSpec ^Schedule$Builder %1 (schedule-spec-> %2))
   :state                 #(.setState ^Schedule$Builder %1 (schedule-state-> %2))})

(defn schedule->
  (^Schedule [params]
   (u/build (Schedule/newBuilder) schedule-spec params))
  (^Schedule [schedule params]
   (u/build (Schedule/newBuilder schedule) schedule-spec params)))

(defn schedule-update->
  "Builds a `ScheduleUpdate` from an existing `schedule` and reschedule `params`.

   When `params` contains `:schedule :search-attributes`, the schedule-level
   search attributes are updated using the two-arg `ScheduleUpdate`
   constructor (both simple and typed input formats are accepted). The
   supplied map replaces the entire set: existing attributes not present in
   the map are removed, and an empty map clears them all. When the key is
   omitted, the existing search attributes are preserved."
  ^ScheduleUpdate [schedule params]
  (let [updated (schedule-> schedule params)
        sa-input (get-in params [:schedule :search-attributes])]
    (if (some? sa-input)
      (ScheduleUpdate. updated (sa/search-attributes-> sa-input))
      (ScheduleUpdate. updated))))
