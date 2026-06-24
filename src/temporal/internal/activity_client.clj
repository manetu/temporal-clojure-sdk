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

;; ActivityClient interface exposes only typed overloads.  The implementation
;; (ActivityClientImpl) additionally provides an untyped
;; start(String, StartActivityOptions, Object...) method.  We locate it once
;; per concrete class and cache the Method for subsequent calls.
(defonce ^:private untyped-start-cache (atom {}))

(defn- find-untyped-start-method
  [^Class client-class]
  (or (get @untyped-start-cache client-class)
      (let [m (->> (.getMethods client-class)
                   (filter (fn [^java.lang.reflect.Method m]
                             (let [^"[Ljava.lang.Class;" params (.getParameterTypes m)]
                               (and (= "start" (.getName m))
                                    (= 3 (alength params))
                                    (= String (aget params 0))
                                    (= StartActivityOptions (aget params 1))))))
                   first)]
        (when m (.setAccessible ^java.lang.reflect.Method m true))
        (swap! untyped-start-cache assoc client-class m)
        m)))

(defn start-untyped-handle
  "Dispatches an activity by name string using ActivityClient's untyped start overload.
  Returns a UntypedActivityHandle."
  ^UntypedActivityHandle [^ActivityClient client ^String activity-name opts args]
  (let [options (start-activity-options-> opts)
        m       (find-untyped-start-method (class client))]
    (when-not m
      (throw (ex-info "ActivityClient does not expose an untyped start method; requires temporal-shaded >= 1.36.0"
                      {:client-class (class client)})))
    (.invoke ^java.lang.reflect.Method m client
             (into-array Object [activity-name options (into-array Object [args])]))))
