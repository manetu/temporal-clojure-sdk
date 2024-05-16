(ns temporal.internal.child-workflow
  (:require [temporal.common :as common]
            [temporal.internal.utils :as u]
            [temporal.internal.workflow :as w])
  (:import [java.time Duration]
           [io.temporal.api.enums.v1 ParentClosePolicy]
           [io.temporal.workflow ChildWorkflowOptions ChildWorkflowOptions$Builder ChildWorkflowCancellationType]))

(def cancellation-type->
  {:abandon                     ChildWorkflowCancellationType/ABANDON
   :try-cancel                  ChildWorkflowCancellationType/TRY_CANCEL
   :wait-cancellation-completed ChildWorkflowCancellationType/WAIT_CANCELLATION_COMPLETED
   :wait-cancellation-requested ChildWorkflowCancellationType/WAIT_CANCELLATION_REQUESTED})

(def workflow-parent-close-policy->
  {:abandon ParentClosePolicy/PARENT_CLOSE_POLICY_ABANDON
   :request-cancel ParentClosePolicy/PARENT_CLOSE_POLICY_REQUEST_CANCEL
   :terminate ParentClosePolicy/PARENT_CLOSE_POLICY_TERMINATE})

(def ^:no-doc child-workflow-option-spec
  {:task-queue                 #(.setTaskQueue ^ChildWorkflowOptions$Builder %1 (u/namify %2))
   :workflow-id                #(.setWorkflowId ^ChildWorkflowOptions$Builder %1 (u/namify %2))
   :workflow-id-reuse-policy   #(.setWorkflowIdReusePolicy ^ChildWorkflowOptions$Builder %1 (w/workflow-id-reuse-policy-> %2))
   :parent-close-policy        #(.setParentClosePolicy ^ChildWorkflowOptions$Builder %1 (workflow-parent-close-policy-> %2))
   :workflow-execution-timeout #(.setWorkflowExecutionTimeout ^ChildWorkflowOptions$Builder %1 %2)
   :workflow-run-timeout       #(.setWorkflowRunTimeout ^ChildWorkflowOptions$Builder %1 %2)
   :workflow-task-timeout      #(.setWorkflowTaskTimeout ^ChildWorkflowOptions$Builder %1 %2)
   :retry-options              #(.setRetryOptions %1 (common/retry-options-> %2))
   :cron-schedule              #(.setCronSchedule ^ChildWorkflowOptions$Builder %1 %2)
   :cancellation-type          #(.setCancellationType ^ChildWorkflowOptions$Builder %1 (cancellation-type-> %2))
   :memo                       #(.setMemo ^ChildWorkflowOptions$Builder %1 %2)
   :search-attributes          #(.setSearchAttributes ^ChildWorkflowOptions$Builder %1 %2)})

(defn import-child-workflow-options
  [{:keys [workflow-run-timeout workflow-execution-timeout] :as params}]
  (cond-> params
    (every? nil? [workflow-run-timeout workflow-execution-timeout])
    (assoc :workflow-execution-timeout (Duration/ofSeconds 10))))

(defn child-workflow-options->
  ^ChildWorkflowOptions [params]
  (u/build (ChildWorkflowOptions/newBuilder) child-workflow-option-spec (import-child-workflow-options params)))
