;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.workflow
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [taoensso.timbre :as log]
            [temporal.internal.utils :as u]
            [temporal.internal.signals :as s])
  (:import [io.temporal.workflow Workflow WorkflowInfo]))

(extend-protocol p/Datafiable
  WorkflowInfo
  (datafy [d]
    {:namespace     (.getNamespace d)
     :workflow-id   (.getWorkflowId d)
     :run-id        (.getRunId d)
     :workflow-type (.getWorkflowType d)}))

(defn get-info []
  (d/datafy (Workflow/getInfo)))

(defn get-annotation
  ^String [x]
  (u/get-annotation x ::def))

(defn execute
  [ctx args]
  (let [{:keys [workflow-type] :as info} (get-info)
        f (u/find-annotated-fn ::def workflow-type)
        signals (s/create)]
    (log/trace "execute:" info)
    (u/wrap-encoded (fn [args] (f ctx {:args args :signals signals})) args)))
