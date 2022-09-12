;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.activity
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [clojure.core.async :refer [go <!] :as async]
            [taoensso.timbre :as log]
            [taoensso.nippy :as nippy]
            [temporal.internal.utils :as u]
            [temporal.internal.common :as common])
  (:import [io.temporal.activity Activity ActivityInfo DynamicActivity]
           [io.temporal.activity ActivityOptions ActivityOptions$Builder LocalActivityOptions LocalActivityOptions$Builder]
           [clojure.core.async.impl.channels ManyToManyChannel]))

(def invoke-option-spec
  {:start-to-close-timeout #(.setStartToCloseTimeout ^ActivityOptions$Builder %1  %2)
   :retry-options          #(.setRetryOptions ^ActivityOptions$Builder %1 (common/retry-options-> %2))})

(defn invoke-options->
  ^ActivityOptions [params]
  (u/build (ActivityOptions/newBuilder) invoke-option-spec params))

(def local-invoke-option-spec
  {:start-to-close-timeout    #(.setStartToCloseTimeout ^LocalActivityOptions$Builder %1  %2)
   :schedule-to-close-timeout #(.setScheduleToCloseTimeout ^LocalActivityOptions$Builder %1  %2)
   :retry-options             #(.setRetryOptions ^LocalActivityOptions$Builder %1 (common/retry-options-> %2))
   :do-not-include-args       #(.setDoNotIncludeArgumentsIntoMarker ^LocalActivityOptions$Builder %1 %2)
   :local-retry-threshold     #(.setLocalRetryThreshold ^LocalActivityOptions$Builder %1 %2)})

(defn local-invoke-options->
  ^LocalActivityOptions [params]
  (u/build (LocalActivityOptions/newBuilder) local-invoke-option-spec params))

(extend-protocol p/Datafiable
  ActivityInfo
  (datafy [d]
    {:task-token     (.getTaskToken d)
     :workflow-id    (.getWorkflowId d)
     :run-id         (.getRunId d)
     :activity-id    (.getActivityId d)
     :activity-type  (.getActivityType d)}))

(defn get-info []
  (-> (Activity/getExecutionContext)
      (.getInfo)
      (d/datafy)))

(defn get-annotation
  ^String [x]
  (u/get-annotated-name x ::def))

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
  [ctx args]
  (let [{:keys [activity-type activity-id] :as info} (get-info)
        f (u/find-annotated-fn ::def activity-type)
        a (u/->args args)]
    (log/trace activity-id "calling" f "with args:" a)
    (try
      (result-> activity-id (f ctx a))
      (catch Exception e
        (log/error e)
        (throw e)))))

(defn dispatcher [ctx]
  (reify DynamicActivity
    (execute [_ args]
      (-execute ctx args))))
