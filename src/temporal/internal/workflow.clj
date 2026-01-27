;; Copyright © Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.workflow
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [slingshot.slingshot :refer [try+]]
            [taoensso.timbre :as log]
            [temporal.common :as common]
            [temporal.internal.exceptions :as e]
            [temporal.internal.signals :as s]
            [temporal.internal.utils :as u])
  (:import [io.temporal.api.enums.v1 WorkflowIdConflictPolicy WorkflowIdReusePolicy]
           [io.temporal.client WorkflowOptions WorkflowOptions$Builder]
           [io.temporal.internal.sync DestroyWorkflowThreadError]
           [io.temporal.workflow Workflow WorkflowInfo]))

(extend-protocol p/Datafiable
  WorkflowInfo
  (datafy [d]
    {:namespace     (.getNamespace d)
     :workflow-id   (.getWorkflowId d)
     :run-id        (.getRunId d)
     :workflow-type (.getWorkflowType d)
     :attempt       (.getAttempt d)}))

(def workflow-id-reuse-options
  "
| Value                        | Description                                                                  |
| ---------------------------- | --------------------------------------------------------------------------- |
| :allow-duplicate             | Allow starting a workflow execution using the same workflow id.             |
| :allow-duplicate-failed-only | Allow starting a workflow execution using the same workflow id, only when the last execution's final state is one of [terminated, cancelled, timed out, failed] |
| :reject-duplicate            | Do not permit re-use of the workflow id for this workflow.                  |
| :terminate-if-running        | If a workflow is running using the same workflow ID, terminate it and start a new one. If no running workflow, then the behavior is the same as ALLOW_DUPLICATE|
"
  {:allow-duplicate             WorkflowIdReusePolicy/WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE
   :allow-duplicate-failed-only WorkflowIdReusePolicy/WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY
   :reject-duplicate            WorkflowIdReusePolicy/WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE
   :terminate-if-running        WorkflowIdReusePolicy/WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING})

(defn ^:no-doc workflow-id-reuse-policy->
  ^WorkflowIdReusePolicy [policy]
  (or (get workflow-id-reuse-options policy)
      (throw (IllegalArgumentException. (str "Unknown workflow-id-reuse-policy: " policy " Must be one of " (keys workflow-id-reuse-options))))))

(def workflow-id-conflict-options
  "
| Value               | Description                                                                  |
| ------------------- | --------------------------------------------------------------------------- |
| :fail               | Don't start a new workflow, fail with 'workflow already started' error. (Default) |
| :use-existing       | Don't start a new workflow, return a handle for the running workflow.       |
| :terminate-existing | Terminate the running workflow before starting a new one.                   |
"
  {:fail               WorkflowIdConflictPolicy/WORKFLOW_ID_CONFLICT_POLICY_FAIL
   :use-existing       WorkflowIdConflictPolicy/WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING
   :terminate-existing WorkflowIdConflictPolicy/WORKFLOW_ID_CONFLICT_POLICY_TERMINATE_EXISTING})

(defn ^:no-doc workflow-id-conflict-policy->
  ^WorkflowIdConflictPolicy [policy]
  (or (get workflow-id-conflict-options policy)
      (throw (IllegalArgumentException. (str "Unknown workflow-id-conflict-policy: " policy " Must be one of " (keys workflow-id-conflict-options))))))

(def ^:no-doc wf-option-spec
  {:task-queue                  #(.setTaskQueue ^WorkflowOptions$Builder %1 (u/namify %2))
   :workflow-id                 #(.setWorkflowId ^WorkflowOptions$Builder %1 (u/namify %2))
   :workflow-id-reuse-policy    #(.setWorkflowIdReusePolicy ^WorkflowOptions$Builder %1 (workflow-id-reuse-policy-> %2))
   :workflow-id-conflict-policy #(.setWorkflowIdConflictPolicy ^WorkflowOptions$Builder %1 (workflow-id-conflict-policy-> %2))
   :workflow-execution-timeout  #(.setWorkflowExecutionTimeout ^WorkflowOptions$Builder %1 %2)
   :workflow-run-timeout        #(.setWorkflowRunTimeout ^WorkflowOptions$Builder %1 %2)
   :workflow-task-timeout       #(.setWorkflowTaskTimeout ^WorkflowOptions$Builder %1 %2)
   :retry-options               #(.setRetryOptions %1 (common/retry-options-> %2))
   :cron-schedule               #(.setCronSchedule ^WorkflowOptions$Builder %1 %2)
   :memo                        #(.setMemo ^WorkflowOptions$Builder %1 %2)
   :search-attributes           #(.setSearchAttributes ^WorkflowOptions$Builder %1 %2)
   :start-delay                 #(.setStartDelay ^WorkflowOptions$Builder %1 %2)})

(defn ^:no-doc wf-options->
  ^WorkflowOptions [params]
  (u/build (WorkflowOptions/newBuilder (WorkflowOptions/getDefaultInstance)) wf-option-spec params))

(defn get-info []
  (d/datafy (Workflow/getInfo)))

(defn get-annotated-name
  ^String [x]
  (u/get-annotated-name x ::def))

(defn auto-dispatch
  []
  (u/get-annotated-fns ::def))

(defn import-dispatch
  [syms]
  (u/import-dispatch ::def syms))

(defn execute
  [ctx dispatch args]
  (let [{:keys [workflow-type workflow-id]} (get-info)]
    (try+
     (let [d (u/find-dispatch dispatch workflow-type)
           f (:fn d)
           a (u/->args args)
           _ (log/trace workflow-id "calling" f "with args:" a)
           r (if (-> d :type (= :legacy))
               (f ctx {:args a :signals (s/create-signal-chan)})
               (f a))]
       (log/trace workflow-id "result:" r)
       r)
     (catch DestroyWorkflowThreadError ex
       (log/debug workflow-id "thread evicted")
       (throw ex))
     (catch Object o
       (log/error &throw-context)
       (e/forward &throw-context)))))
