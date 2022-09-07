;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.activity
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [taoensso.timbre :as log]
            [temporal.internal.utils :as u]
            [temporal.internal.common :as common])
  (:import [io.temporal.activity Activity ActivityInfo DynamicActivity]
           [io.temporal.activity ActivityOptions ActivityOptions$Builder]))

(def invoke-option-spec
  {:start-to-close-timeout #(.setStartToCloseTimeout ^ActivityOptions$Builder %1  %2)
   :retry-options          #(.setRetryOptions %1 (common/retry-options-> %2))})

(defn invoke-options->
  ^ActivityOptions [params]
  (u/build (ActivityOptions/newBuilder) invoke-option-spec params))

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
  (u/get-annotation x ::def))

(defn- -execute
  [ctx args]
  (let [{:keys [activity-type] :as info} (get-info)
        f (u/find-annotated-fn ::def activity-type)]
    (log/trace "execute:" info)
    (u/wrap-encoded (partial f ctx) args)))

(defn dispatcher [ctx]
  (reify DynamicActivity
    (execute [_ args]
      (-execute ctx args))))
