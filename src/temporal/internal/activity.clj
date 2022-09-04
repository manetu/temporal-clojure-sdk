;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.activity
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [taoensso.timbre :as log]
            [temporal.internal.utils :as u])
  (:import [io.temporal.activity Activity ActivityInfo DynamicActivity]))

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
