;; Copyright © Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.activity-client
  (:require [temporal.common :as common]
            [temporal.internal.activity :as a]
            [temporal.internal.utils :as u])
  (:import [io.temporal.api.enums.v1 ActivityIdConflictPolicy ActivityIdReusePolicy]
           [io.temporal.client ActivityClient StartActivityOptions StartActivityOptions$Builder UntypedActivityHandle]
           [java.time Duration]))

(def id-reuse-policy->
  {:allow-duplicate             ActivityIdReusePolicy/ACTIVITY_ID_REUSE_POLICY_ALLOW_DUPLICATE
   :allow-duplicate-failed-only ActivityIdReusePolicy/ACTIVITY_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY
   :reject-duplicate            ActivityIdReusePolicy/ACTIVITY_ID_REUSE_POLICY_REJECT_DUPLICATE})

(def id-conflict-policy->
  {:fail         ActivityIdConflictPolicy/ACTIVITY_ID_CONFLICT_POLICY_FAIL
   :use-existing ActivityIdConflictPolicy/ACTIVITY_ID_CONFLICT_POLICY_USE_EXISTING})

(def start-activity-option-spec
  {:id                        #(.setId ^StartActivityOptions$Builder %1 %2)
   :task-queue                #(.setTaskQueue ^StartActivityOptions$Builder %1 (u/namify %2))
   :id-reuse-policy           #(.setIdReusePolicy ^StartActivityOptions$Builder %1 (id-reuse-policy-> %2))
   :id-conflict-policy        #(.setIdConflictPolicy ^StartActivityOptions$Builder %1 (id-conflict-policy-> %2))
   :schedule-to-close-timeout #(.setScheduleToCloseTimeout ^StartActivityOptions$Builder %1 %2)
   :schedule-to-start-timeout #(.setScheduleToStartTimeout ^StartActivityOptions$Builder %1 %2)
   :start-to-close-timeout    #(.setStartToCloseTimeout ^StartActivityOptions$Builder %1 %2)
   :heartbeat-timeout         #(.setHeartbeatTimeout ^StartActivityOptions$Builder %1 %2)
   :retry-options             #(.setRetryOptions ^StartActivityOptions$Builder %1 (common/retry-options-> %2))
   :start-delay               #(.setStartDelay ^StartActivityOptions$Builder %1 %2)
   :static-summary            #(.setStaticSummary ^StartActivityOptions$Builder %1 %2)
   :static-details            #(.setStaticDetails ^StartActivityOptions$Builder %1 %2)})

(defn- import-start-options
  [{:keys [id start-to-close-timeout schedule-to-close-timeout] :as params}]
  (cond-> params
    (nil? id)
    (assoc :id (str (java.util.UUID/randomUUID)))
    (every? nil? [start-to-close-timeout schedule-to-close-timeout])
    (assoc :start-to-close-timeout (Duration/ofSeconds 3))))

(defn start-activity-options->
  ^StartActivityOptions [params]
  (u/build (StartActivityOptions/newBuilder) start-activity-option-spec (import-start-options params)))

(defn resolve-activity-name
  "Resolves a defactivity var, keyword, or string to an activity type name string."
  ^String [activity]
  (if (fn? activity)
    (a/get-annotation activity)
    (u/namify activity)))

;; ActivityClient declares the untyped start(String, StartActivityOptions, Object...)
;; overload directly on the public interface, so no reflection is required to reach it.
(defn start-untyped-handle
  "Dispatches an activity by name string using ActivityClient's untyped start overload.
  Returns a UntypedActivityHandle."
  ^UntypedActivityHandle [^ActivityClient client ^String activity-name opts args]
  (let [options (start-activity-options-> opts)]
    (.start client activity-name options (u/->objarray args))))

(defn get-handle
  "Returns a handle to a previously-started Standalone Activity, identified by activity-id
  and (optionally) activity-run-id."
  (^UntypedActivityHandle [^ActivityClient client ^String activity-id]
   (.getHandle client activity-id nil))
  (^UntypedActivityHandle [^ActivityClient client ^String activity-id activity-run-id]
   (.getHandle client activity-id ^String activity-run-id)))
