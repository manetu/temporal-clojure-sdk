;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.workflow
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [taoensso.timbre :as log]
            [temporal.internal.common :as common]
            [temporal.internal.utils :as u]
            [temporal.internal.signals :as s])
  (:import [io.temporal.workflow Workflow WorkflowInfo]
           [io.temporal.client WorkflowOptions WorkflowOptions$Builder]))

(def wf-option-spec
  {:task-queue    #(.setTaskQueue ^WorkflowOptions$Builder %1 (u/namify %2))
   :workflow-id   #(.setWorkflowId ^WorkflowOptions$Builder %1 %2)
   :retry-options #(.setRetryOptions %1 (common/retry-options-> %2))})

(defn wf-options->
  ^WorkflowOptions [params]
  (u/build (WorkflowOptions/newBuilder) wf-option-spec params))

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
        f (u/find-annotated-fn ::def workflow-type)]
    (log/trace "execute:" info)
    (u/wrap-encoded (fn [args] (f ctx {:args args :signals (s/create)})) args)))
