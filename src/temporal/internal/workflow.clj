;; Copyright © Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.workflow
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [slingshot.slingshot :refer [try+]]
            [taoensso.timbre :as log]
            [taoensso.nippy :as nippy]
            [temporal.common :as common]
            [temporal.internal.utils :as u]
            [temporal.internal.signals :as s]
            [temporal.internal.exceptions :as e])
  (:import [io.temporal.workflow Workflow WorkflowInfo]
           [io.temporal.client WorkflowOptions WorkflowOptions$Builder]))

(def wf-option-spec
  {:task-queue                 #(.setTaskQueue ^WorkflowOptions$Builder %1 (u/namify %2))
   :workflow-id                #(.setWorkflowId ^WorkflowOptions$Builder %1 %2)
   :workflow-execution-timeout #(.setWorkflowExecutionTimeout ^WorkflowOptions$Builder %1 %2)
   :workflow-run-timeout       #(.setWorkflowRunTimeout ^WorkflowOptions$Builder %1 %2)
   :workflow-task-timeout      #(.setWorkflowTaskTimeout ^WorkflowOptions$Builder %1 %2)
   :retry-options              #(.setRetryOptions %1 (common/retry-options-> %2))
   :cron-schedule              #(.setCronSchedule ^WorkflowOptions$Builder %1 %2)
   :memo                       #(.setMemo ^WorkflowOptions$Builder %1 %2)
   :search-attributes          #(.setSearchAttributes ^WorkflowOptions$Builder %1 %2)})

(defn wf-options->
  ^WorkflowOptions [params]
  (u/build (WorkflowOptions/newBuilder (WorkflowOptions/getDefaultInstance)) wf-option-spec params))

(extend-protocol p/Datafiable
  WorkflowInfo
  (datafy [d]
    {:namespace     (.getNamespace d)
     :workflow-id   (.getWorkflowId d)
     :run-id        (.getRunId d)
     :workflow-type (.getWorkflowType d)}))

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
  (try+
   (let [{:keys [workflow-type workflow-id]} (get-info)
         d (u/find-dispatch dispatch workflow-type)
         f (:fn d)
         a (u/->args args)
         _ (log/trace workflow-id "calling" f "with args:" a)
         r (if (-> d :type (= :legacy))
             (f ctx {:args a :signals (s/create-signal-chan)})
             (f a))]
     (log/trace workflow-id "result:" r)
     (nippy/freeze r))
   (catch Exception e
     (log/error e)
     (throw e))
   (catch Object o
     (log/error &throw-context)
     (e/freeze &throw-context))))
