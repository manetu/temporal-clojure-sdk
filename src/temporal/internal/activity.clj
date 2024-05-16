;; Copyright © Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.activity
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [clojure.core.async :refer [go <!] :as async]
            [slingshot.slingshot :refer [try+]]
            [taoensso.timbre :as log]
            [taoensso.nippy :as nippy]
            [temporal.internal.utils :as u]
            [temporal.internal.exceptions :as e]
            [temporal.common :as common])
  (:import [java.time Duration]
           [io.temporal.internal.sync DestroyWorkflowThreadError]
           [io.temporal.activity Activity ActivityInfo DynamicActivity ActivityCancellationType]
           [io.temporal.activity ActivityOptions ActivityOptions$Builder LocalActivityOptions LocalActivityOptions$Builder]
           [clojure.core.async.impl.channels ManyToManyChannel]))

(def cancellation-type->
  {:abandon                     ActivityCancellationType/ABANDON
   :try-cancel                  ActivityCancellationType/TRY_CANCEL
   :wait-cancellation-completed ActivityCancellationType/WAIT_CANCELLATION_COMPLETED})

(def invoke-option-spec
  {:cancellation-type         #(.setCancellationType ^ActivityOptions$Builder %1 (cancellation-type-> %2))
   :heartbeat-timeout         #(.setHeartbeatTimeout ^ActivityOptions$Builder %1 %2)
   :retry-options             #(.setRetryOptions ^ActivityOptions$Builder %1 (common/retry-options-> %2))
   :start-to-close-timeout    #(.setStartToCloseTimeout ^ActivityOptions$Builder %1 %2)
   :schedule-to-close-timeout #(.setScheduleToCloseTimeout ^ActivityOptions$Builder %1 %2)
   :schedule-to-start-timeout #(.setScheduleToStartTimeout ^ActivityOptions$Builder %1 %2)
   :task-queue                #(.setTaskQueue ^ActivityOptions$Builder %1 %2)})

(defn import-invoke-options
  [{:keys [start-to-close-timeout schedule-to-close-timeout] :as params}]
  (cond-> params
    (every? nil? [start-to-close-timeout schedule-to-close-timeout])
    (assoc :start-to-close-timeout (Duration/ofSeconds 3))))

(defn invoke-options->
  ^ActivityOptions [params]
  (u/build (ActivityOptions/newBuilder) invoke-option-spec (import-invoke-options params)))

(def local-invoke-option-spec
  {:start-to-close-timeout    #(.setStartToCloseTimeout ^LocalActivityOptions$Builder %1  %2)
   :schedule-to-close-timeout #(.setScheduleToCloseTimeout ^LocalActivityOptions$Builder %1  %2)
   :retry-options             #(.setRetryOptions ^LocalActivityOptions$Builder %1 (common/retry-options-> %2))
   :do-not-include-args       #(.setDoNotIncludeArgumentsIntoMarker ^LocalActivityOptions$Builder %1 %2)
   :local-retry-threshold     #(.setLocalRetryThreshold ^LocalActivityOptions$Builder %1 %2)})

(defn local-invoke-options->
  ^LocalActivityOptions [params]
  (u/build (LocalActivityOptions/newBuilder (LocalActivityOptions/getDefaultInstance)) local-invoke-option-spec (import-invoke-options params)))

(extend-protocol p/Datafiable
  ActivityInfo
  (datafy [d]
    {:task-token     (.getTaskToken d)
     :workflow-id    (.getWorkflowId d)
     :run-id         (.getRunId d)
     :activity-id    (.getActivityId d)
     :activity-type  (.getActivityType d)}))

(defn get-info []
  (->> (Activity/getExecutionContext)
       (.getInfo)
       (d/datafy)
       (into {})))

(defn get-annotation
  ^String [x]
  (u/get-annotated-name x ::def))

(defn auto-dispatch
  []
  (u/get-annotated-fns ::def))

(defn import-dispatch
  [syms]
  (u/import-dispatch ::def syms))

(defn- export-result [activity-id x]
  (log/trace activity-id "result:" x)
  (nippy/freeze x))

(defmulti result-> (fn [_ r] (type r)))

(defmethod result-> ManyToManyChannel
  [activity-id x]
  (let [ctx        (Activity/getExecutionContext)
        completion (.useLocalManualCompletion ctx)]
    (go
      (let [r (<! x)]
        (if (instance? Throwable r)
          (do
            (log/error r)
            (.fail completion r))
          (.complete completion (export-result activity-id r)))))))

(defmethod result-> :default
  [activity-id x]
  (export-result activity-id x))

(defn- -execute
  [ctx dispatch args]
  (let [{:keys [activity-type activity-id] :as _info} (get-info)
        f (u/find-dispatch-fn dispatch activity-type)
        a (u/->args args)]
    (log/trace activity-id "calling" f "with args:" a)
    (try+
     (result-> activity-id (f ctx a))
     (catch DestroyWorkflowThreadError ex
       (log/debug activity-id "thread evicted")
       (throw ex))
     (catch Object o
       (log/error &throw-context)
       (e/forward &throw-context)))))

(defn dispatcher [ctx dispatch]
  (reify DynamicActivity
    (execute [_ args]
      (-execute ctx dispatch args))))
