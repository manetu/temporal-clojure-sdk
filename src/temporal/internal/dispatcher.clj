;; Copyright © 2022 Manetu, Inc.  All rights reserved

(ns ^:no-doc temporal.internal.dispatcher
  (:require [temporal.internal.workflow :as wf]))

(gen-class
 :name "temporal.internal.dispatcher.WorkflowImpl"
 :implements [io.temporal.workflow.DynamicWorkflow]
 :state "ctx"
 :init "init"
 :constructors {[Object] []}
 :prefix "workflow-")

(defn workflow-init
  [ctx]
  [[] ctx])

(defn workflow-execute
  [^temporal.internal.dispatcher.WorkflowImpl this args]
  (wf/execute (.ctx this) args))
